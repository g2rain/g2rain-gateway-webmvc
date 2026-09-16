package com.g2rain.gateway.cache;

import com.g2rain.basis.enums.BasisSyncerEnum;
import com.g2rain.basis.vo.SessionApiPermissionVo;
import com.g2rain.common.enums.SessionType;
import com.g2rain.common.model.Result;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Collections;
import com.g2rain.gateway.client.AuthorityClient;
import com.g2rain.gateway.model.cache.MemberOrganPermission;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MEMBER 接口权限缓存：按 organId 懒加载，{@code MEMBER_PERM} 按 organ 失效。
 */
@Slf4j
@Service
@AllArgsConstructor
public class MemberPerm extends AbstractMessageStorage<Long, Long, Long> {

    private static final Executor VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final AuthorityClient authorityClient;

    private static final Cache<Long, MemberOrganPermission> MEMBER_API_PERMISSIONS = Caffeine
        .newBuilder()
        .maximumSize(50_000)
        .expireAfterAccess(6, TimeUnit.HOURS)
        .build();

    private final ConcurrentHashMap<Long, CompletableFuture<MemberOrganPermission>> inFlightLoads =
        new ConcurrentHashMap<>();

    @Override
    protected @NonNull String dataSource() {
        return BasisSyncerEnum.MEMBER_PERM.name();
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
        MEMBER_API_PERMISSIONS.invalidate(key);
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
     * @return 是否具备权限；回源失败抛出运行时异常，由过滤器映射为权限缓存不可用
     */
    public boolean hasApiPermission(Long organId, Long apiId) {
        if (Objects.isNull(organId) || organId <= 0 || Objects.isNull(apiId)) {
            return false;
        }
        return getOrLoad(organId).apiIds().contains(apiId);
    }

    public MemberOrganPermission getOrLoad(Long organId) {
        if (Objects.isNull(organId) || organId <= 0) {
            throw new IllegalArgumentException("organId");
        }

        MemberOrganPermission cached = MEMBER_API_PERMISSIONS.getIfPresent(organId);
        if (Objects.nonNull(cached)) {
            return cached;
        }

        CompletableFuture<MemberOrganPermission> shared = inFlightLoads.computeIfAbsent(organId, id -> {
            CompletableFuture<MemberOrganPermission> future = CompletableFuture.supplyAsync(
                () -> loadAndCache(id), VIRTUAL_THREAD_EXECUTOR);
            future.whenComplete((_, _) -> inFlightLoads.remove(id, future));
            return future;
        });

        return shared.join();
    }

    private MemberOrganPermission loadAndCache(Long organId) {
        Result<SessionApiPermissionVo> result = authorityClient.getSessionApiPermissions(
            SessionType.MEMBER.name(), organId
        );
        if (Objects.isNull(result) || !result.isSuccess() || Objects.isNull(result.getData())) {
            throw new IllegalStateException("MEMBER session api permissions unavailable");
        }

        MemberOrganPermission perm = toPermission(organId, result.getData());
        MEMBER_API_PERMISSIONS.put(organId, perm);
        return perm;
    }

    private static MemberOrganPermission toPermission(Long organId, SessionApiPermissionVo vo) {
        Set<Long> apiIds = new HashSet<>();
        if (Objects.nonNull(vo) && Collections.isNotEmpty(vo.getApiIds())) {
            for (Long id : vo.getApiIds()) {
                if (Objects.nonNull(id)) {
                    apiIds.add(id);
                }
            }
        }
        long version = Objects.nonNull(vo) && Objects.nonNull(vo.getVersion()) ? vo.getVersion() : 0L;
        return new MemberOrganPermission(organId, version, Set.copyOf(apiIds));
    }
}
