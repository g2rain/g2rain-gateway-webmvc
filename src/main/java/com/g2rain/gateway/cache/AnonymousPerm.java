package com.g2rain.gateway.cache;

import com.g2rain.basis.enums.BasisSyncerEnum;
import com.g2rain.basis.vo.SessionApiPermissionVo;
import com.g2rain.common.enums.SessionType;
import com.g2rain.common.model.Result;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Collections;
import com.g2rain.gateway.client.AuthorityClient;
import com.g2rain.gateway.model.cache.AnonymousOrganPermission;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ANONYMOUS 接口权限缓存：按 organId 懒加载"可用接口 + 启用中的接入码"，{@code ANONYMOUS_PERM} 按机构失效。
 */
@Service
@AllArgsConstructor
public class AnonymousPerm extends AbstractMessageStorage<Long, Long, Long> {

    private static final Executor VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final AuthorityClient authorityClient;

    private static final Cache<Long, AnonymousOrganPermission> ANONYMOUS_PERMISSIONS = Caffeine
        .newBuilder()
        .maximumSize(50_000)
        .expireAfterAccess(6, TimeUnit.HOURS)
        .build();

    private final ConcurrentHashMap<Long, CompletableFuture<AnonymousOrganPermission>> inFlightLoads =
        new ConcurrentHashMap<>();

    @Override
    protected @NonNull String dataSource() {
        return BasisSyncerEnum.ANONYMOUS_PERM.name();
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
        ANONYMOUS_PERMISSIONS.invalidate(key);
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
     * 接入码仍启用且机构已开通该接口时放行；未命中缓存时回源 Basis。
     *
     * @throws IllegalStateException 回源失败时抛出
     */
    public boolean hasApiPermission(Long organId, Long entryId, Long apiId) {
        if (Objects.isNull(organId) || organId <= 0 || Objects.isNull(entryId) || Objects.isNull(apiId)) {
            return false;
        }
        return getOrLoad(organId).allows(entryId, apiId);
    }

    /**
     * 读取或懒加载机构 ANONYMOUS 权限快照；同一机构并发回源只发一次请求。
     */
    public AnonymousOrganPermission getOrLoad(Long organId) {
        AnonymousOrganPermission cached = ANONYMOUS_PERMISSIONS.getIfPresent(organId);
        if (Objects.nonNull(cached)) {
            return cached;
        }

        CompletableFuture<AnonymousOrganPermission> shared = inFlightLoads.computeIfAbsent(organId, id -> {
            CompletableFuture<AnonymousOrganPermission> future = CompletableFuture.supplyAsync(
                () -> loadAndCache(id), VIRTUAL_THREAD_EXECUTOR);
            future.whenComplete((_, _) -> inFlightLoads.remove(id, future));
            return future;
        });
        return shared.join();
    }

    private AnonymousOrganPermission loadAndCache(Long organId) {
        Result<SessionApiPermissionVo> result = authorityClient.getSessionApiPermissions(
            SessionType.ANONYMOUS.name(), organId
        );
        if (Objects.isNull(result) || !result.isSuccess() || Objects.isNull(result.getData())) {
            throw new IllegalStateException("ANONYMOUS session api permissions unavailable");
        }

        SessionApiPermissionVo vo = result.getData();
        long version = Objects.nonNull(vo.getVersion()) ? vo.getVersion() : 0L;
        AnonymousOrganPermission perm = new AnonymousOrganPermission(
            organId, version, toSet(vo.getApiIds()), toSet(vo.getEntryIds()));
        ANONYMOUS_PERMISSIONS.put(organId, perm);
        return perm;
    }

    private static Set<Long> toSet(List<Long> ids) {
        if (Collections.isEmpty(ids)) {
            return Set.of();
        }
        return ids.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }
}
