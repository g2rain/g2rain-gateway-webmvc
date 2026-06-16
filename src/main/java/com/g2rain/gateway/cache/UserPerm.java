package com.g2rain.gateway.cache;


import com.g2rain.basis.enums.BasisSyncerEnum;
import com.g2rain.basis.vo.BaseAuthorityApiVo;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Collections;
import com.g2rain.gateway.client.AuthorityClient;
import com.g2rain.gateway.model.cache.BaseAuthority;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 用户接口权限缓存
 *
 * @author alpha
 * @since 2026/5/5
 */
@Slf4j
@Service
@AllArgsConstructor
public class UserPerm extends AbstractMessageStorage<Long, Long, Long> {

    /**
     * 权限回源合并任务在虚拟线程上执行，避免占用 {@link java.util.concurrent.ForkJoinPool#commonPool()}。
     */
    private static final Executor VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final AuthorityClient authorityClient;

    /**
     * 用户的接口能力缓存
     * 外层 Key 是机构标识（便于按机构整棵失效）
     * 第二层 Key 是用户标识
     * 第三层 Key 是应用标识（与远端 {@code getApiPermissions(userId, applicationId)} 一致，避免同一用户多应用互相覆盖）
     * 最内层 Key 是接口标识
     */
    private static final Cache<Long, Map<Long, Map<Long, Map<Long, BaseAuthority>>>> USER_API_PERMISSIONS = Caffeine
        .newBuilder()
        .maximumSize(100_000)
        .expireAfterAccess(6, TimeUnit.HOURS)
        .build();

    /**
     * 同一 (机构, 用户, 应用) 缓存未命中时，合并为单次 Authority RPC。
     */
    private final ConcurrentHashMap<LoadKey, CompletableFuture<Map<Long, BaseAuthority>>> inFlightLoads = new ConcurrentHashMap<>();

    @Override
    protected @NonNull String dataSource() {
        return BasisSyncerEnum.USER_PERM.name();
    }

    @Override
    protected @NonNull Class<Long> getValueType() {
        return Long.class;
    }

    @Override
    protected @NonNull Long getKey(@NonNull Long value) {
        return value;
    }

    @Override
    protected void create(@NonNull Long key, Long value) {
        delete(key);
    }

    @Override
    protected void delete(@NonNull Long key) {
        USER_API_PERMISSIONS.invalidate(key);
    }

    @Override
    protected void update(@NonNull Long key, Long value) {
        delete(key);
    }

    @Override
    protected Long get(@NonNull Long key) {
        return key;
    }

    /**
     * 读取用户对某接口的权限；缓存未命中时拉取该用户在当前应用下的全部接口权限并写入缓存。
     * <p>
     * 同一 {@code organId} 下多用户并发填充时，通过 {@link Cache#asMap()} {@code compute(organId, ...)} 合并，
     * 避免整棵机构 Map 被后写覆盖导致丢用户数据。
     * </p>
     * <p>同一 {@code (organId, userId, appId)} 并发 miss 时合并为单次远端调用。</p>
     */
    public BaseAuthority getApiPermission(Long organId, Long userId, List<Long> roleIds, Long appId, Long apiId) {
        if (Objects.isNull(organId) || Objects.isNull(userId) || Objects.isNull(appId) || Objects.isNull(apiId)) {
            return null;
        }

        Map<Long, BaseAuthority> snapshot = getCachedSnapshot(organId, userId, appId);
        if (Objects.nonNull(snapshot)) {
            return snapshot.get(apiId);
        }

        LoadKey loadKey = new LoadKey(organId, userId, appId);
        CompletableFuture<Map<Long, BaseAuthority>> shared = inFlightLoads.computeIfAbsent(loadKey, k -> {
            CompletableFuture<Map<Long, BaseAuthority>> future = CompletableFuture.supplyAsync(
                () -> loadAndMerge(organId, userId, roleIds, appId), VIRTUAL_THREAD_EXECUTOR);
            future.whenComplete((_, _) -> inFlightLoads.remove(k, future));
            return future;
        });

        try {
            Map<Long, BaseAuthority> loaded = shared.join();
            return loaded.get(apiId);
        } catch (Exception e) {
            log.warn("加载用户接口权限失败 organId={} userId={} appId={}", organId, userId, appId, e);
            return null;
        }
    }

    private Map<Long, BaseAuthority> loadAndMerge(Long organId, Long userId, List<Long> roleIds, Long appId) {
        var result = authorityClient.getApiPermissions(userId, roleIds, appId);
        if (Objects.isNull(result) || !result.isSuccess()) {
            // 与原先一致：失败不写缓存，便于下次请求重试；仅合并本次 in-flight 的返回值
            return new ConcurrentHashMap<>();
        }

        Map<Long, BaseAuthority> loaded = new ConcurrentHashMap<>();
        List<BaseAuthorityApiVo> rows = result.getData();
        if (!Collections.isEmpty(rows)) {
            for (BaseAuthorityApiVo vo : rows) {
                if (Objects.isNull(vo) || Objects.isNull(vo.getId())) {
                    continue;
                }

                loaded.put(vo.getId(), toBaseAuthority(vo));
            }
        }

        mergeAppPermissions(organId, userId, appId, loaded);
        return loaded;
    }

    private static void mergeAppPermissions(Long organId, Long userId, Long appId, Map<Long, BaseAuthority> loaded) {
        USER_API_PERMISSIONS.asMap().compute(organId, (_, o) ->
            Objects.requireNonNullElseGet(o, ConcurrentHashMap::new)
        ).computeIfAbsent(userId, _ -> new ConcurrentHashMap<>()).put(appId, loaded);
    }

    /**
     * @return 已缓存的该用户在该应用下的权限表（可能为空 Map）；{@code null} 表示尚未加载过，需要请求远端。
     */
    @SuppressWarnings("ConstantConditions")
    private static Map<Long, BaseAuthority> getCachedSnapshot(Long organId, Long userId, Long appId) {
        var userPerms = USER_API_PERMISSIONS.getIfPresent(organId);
        if (Objects.isNull(userPerms)) {
            return null;
        }

        var appPerms = userPerms.get(userId);
        if (Objects.isNull(appPerms)) {
            return null;
        }

        return appPerms.get(appId);
    }

    private static BaseAuthority toBaseAuthority(BaseAuthorityApiVo vo) {
        BaseAuthority result = new BaseAuthority();
        result.setId(vo.getId());
        result.setStatus(vo.getStatus());
        return result;
    }

    private record LoadKey(Long organId, Long userId, Long appId) {

    }
}
