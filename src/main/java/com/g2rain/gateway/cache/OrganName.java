package com.g2rain.gateway.cache;


import com.g2rain.basis.dto.OrganIdNameMapSelectDto;
import com.g2rain.basis.enums.BasisSyncerEnum;
import com.g2rain.basis.vo.OrganIdNameVo;
import com.g2rain.common.model.Result;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Collections;
import com.g2rain.gateway.client.OrganClient;
import com.g2rain.gateway.model.cache.OrganIdName;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author alpha
 * @since 2026/4/13
 */
@Slf4j
@Service
@AllArgsConstructor
public class OrganName extends AbstractMessageStorage<Long, OrganIdName, String> {

    /**
     * 批量回源合并任务在虚拟线程上执行，避免占用 {@link java.util.concurrent.ForkJoinPool#commonPool()}。
     */
    private static final Executor VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 机构客户端
     */
    private final OrganClient organClient;

    /**
     * 相同「未命中 id 集合」并发回源时合并为单次 Basis 批量查询（键为排序后的 id 列表字符串）。
     */
    private final ConcurrentHashMap<String, CompletableFuture<Map<String, String>>> inFlightLoads = new ConcurrentHashMap<>();

    /**
     * 机构名称本地缓存（网关侧）。
     *
     * <p><b>数据来源：</b></p>
     * <ul>
     *   <li>正常路径：下游微服务（basis）发生变更后，通过 cache-sync 推送事件，触发本类的 create/update/delete，从而实时更新缓存</li>
     *   <li>兜底路径：网关在拼装响应时若缓存未命中，会批量回源查询并回填缓存（见 {@link #getNames(Set)})</li>
     * </ul>
     *
     * <p><b>过期策略（expireAfterAccess）：</b></p>
     * <ul>
     *   <li>网关读多写少，且命中率通常很高；按“访问续期”更贴近热数据常驻的使用模式</li>
     *   <li>即便 cache-sync 消息在极端情况下丢失/延迟，缓存也会在一段时间后自然淘汰并触发回源修正</li>
     * </ul>
     *
     * <p><b>负缓存：</b></p>
     * <ul>
     *   <li>对不存在的 id 写入空字符串，降低穿透带来的回源放大</li>
     *   <li>调用方写回响应时会跳过空字符串（见 {@code ResponseAdjustFilter} 对 name 的判空逻辑）</li>
     * </ul>
     *
     * <p><b>作用域：</b>static 单例缓存，全 JVM 共享一份，避免每次注入都创建新缓存实例。</p>
     */
    private static final Cache<Long, String> organCache = Caffeine.newBuilder()
        // 网关侧高频读取，按访问续期更贴近“热数据常驻”的使用模式；漏同步时仍会在较长时间后自然淘汰并触发回源。
        .maximumSize(100_000)
        .expireAfterAccess(6, TimeUnit.HOURS)
        .build();

    @Override
    public @NonNull String dataSource() {
        return BasisSyncerEnum.ORGAN_NAME.name();
    }

    @Override
    public @NonNull Class<OrganIdName> getValueType() {
        return OrganIdName.class;
    }

    @Override
    public @NonNull Long getKey(@NonNull OrganIdName value) {
        return value.getOrganId();
    }

    @Override
    public void create(@NonNull Long key, OrganIdName value) {
        organCache.put(key, value.getOrganName());
    }

    /**
     * 防止缓存穿透
     *
     * @param key 消息键，不能为 {@code null}
     */
    @Override
    public void delete(@NonNull Long key) {
        organCache.put(key, "");
    }

    @Override
    public void update(@NonNull Long key, OrganIdName value) {
        organCache.put(key, value.getOrganName());
    }

    @Override
    public String get(@NonNull Long key) {
        return organCache.getIfPresent(key);
    }

    public Map<String, String> getNames(Set<Long> ids) {
        if (Collections.isEmpty(ids)) {
            return Map.of();
        }

        // 1) 先批量命中本地缓存（包含空字符串的“负缓存”）
        Map<String, String> result = new HashMap<>(Math.max(16, ids.size()));
        Set<Long> missIds = new HashSet<>();
        for (Long id : ids) {
            if (Objects.isNull(id)) {
                continue;
            }

            String cached = organCache.getIfPresent(id);
            if (Objects.nonNull(cached)) {
                result.put(String.valueOf(id), cached);
                continue;
            }

            missIds.add(id);
        }

        if (Collections.isEmpty(missIds)) {
            return result;
        }

        Set<Long> frozenMiss = Set.copyOf(missIds);
        String batchKey = missBatchKey(frozenMiss);
        CompletableFuture<Map<String, String>> shared = inFlightLoads.computeIfAbsent(batchKey, k -> {
            CompletableFuture<Map<String, String>> future = CompletableFuture.supplyAsync(
                () -> materializeMissBatch(frozenMiss), VIRTUAL_THREAD_EXECUTOR);
            future.whenComplete((_, _) -> inFlightLoads.remove(k, future));
            return future;
        });

        Map<String, String> namesForMiss = shared.join();
        for (Long id : frozenMiss) {
            String v = namesForMiss.get(String.valueOf(id));
            if (Objects.nonNull(v)) {
                result.put(String.valueOf(id), v);
            }
        }

        return result;
    }

    private Map<String, String> materializeMissBatch(Set<Long> frozenMiss) {
        OrganIdNameMapSelectDto selectDto = new OrganIdNameMapSelectDto();
        selectDto.setIds(frozenMiss);
        Result<List<OrganIdNameVo>> remoteResult;
        try {
            remoteResult = organClient.selectOrganIdNameMap(selectDto);
        } catch (Exception e) {
            log.warn("批量查询机构名称失败，ids={}", frozenMiss, e);
            return Map.of();
        }

        if (Objects.isNull(remoteResult) || !remoteResult.isSuccess()) {
            log.warn("批量查询机构名称失败，ids={} result={}", frozenMiss, remoteResult);
            return Map.of();
        }

        Set<Long> stillMissing = new HashSet<>(frozenMiss);
        Map<String, String> out = new HashMap<>(Math.max(16, frozenMiss.size()));
        List<OrganIdNameVo> data = remoteResult.getData();
        if (!Collections.isEmpty(data)) {
            for (OrganIdNameVo vo : data) {
                if (Objects.isNull(vo) || Objects.isNull(vo.getOrganId())) {
                    continue;
                }

                Long id = vo.getOrganId();
                String name = Objects.toString(vo.getOrganName(), "");
                organCache.put(id, name);
                out.put(String.valueOf(id), name);
                stillMissing.remove(id);
            }
        }

        for (Long id : stillMissing) {
            organCache.put(id, "");
            out.put(String.valueOf(id), "");
        }

        return out;
    }

    private static String missBatchKey(Set<Long> missIds) {
        return missIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }
}
