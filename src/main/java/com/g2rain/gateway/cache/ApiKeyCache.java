package com.g2rain.gateway.cache;


import com.g2rain.basis.enums.StaticTokenStatus;
import com.g2rain.basis.vo.StaticAccessTokenContextVo;
import com.g2rain.basis.vo.StaticAccessTokenResolveVo;
import com.g2rain.common.model.Result;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.client.LoginTokenClient;
import com.g2rain.gateway.model.auth.ApiKeyResolveResult;
import com.g2rain.gateway.utils.DigestUtils;
import com.g2rain.basis.enums.BasisSyncerEnum;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 静态 API Key 解析缓存。
 *
 * <p>
 * 通过 {@link LoginTokenClient}（Feign）调用 basis 解析令牌：SHA-256 作键、激活态缓存完整上下文、
 * 吊销态缓存轻量标记、不存在不缓存；{@link BasisSyncerEnum#STATIC_ACCESS_TOKEN} 推送 tokenHash 时失效。
 * </p>
 *
 * @author alpha
 * @since 2026/5/22
 */
@Service
@AllArgsConstructor
public class ApiKeyCache extends AbstractMessageStorage<String, String, String> {

    private static final Executor VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final LoginTokenClient loginTokenClient;

    private static final Cache<String, CachedApiKey> CACHE = Caffeine.newBuilder()
        .maximumSize(50_000).expireAfterAccess(30, TimeUnit.MINUTES).build();

    private final ConcurrentHashMap<String, CompletableFuture<ApiKeyResolveResult>> inFlightLoads = new ConcurrentHashMap<>();

    @Override
    protected @NonNull String dataSource() {
        return BasisSyncerEnum.STATIC_ACCESS_TOKEN.name();
    }

    @Override
    protected @NonNull Class<String> getValueType() {
        return String.class;
    }

    @Override
    protected @NonNull String getKey(@NonNull String value) {
        return value;
    }

    @Override
    protected void create(@NonNull String key, String value) {
        delete(key);
    }

    @Override
    protected void delete(@NonNull String key) {
        CACHE.invalidate(key);
    }

    @Override
    protected void update(@NonNull String key, String value) {
        delete(key);
    }

    @Override
    protected String get(@NonNull String key) {
        return key;
    }

    /**
     * @param apiKey 原始 Bearer 凭证
     * @return 无效 / 吊销 / 激活三态
     */
    public ApiKeyResolveResult resolve(String apiKey) {
        if (Strings.isBlank(apiKey)) {
            return ApiKeyResolveResult.invalid();
        }

        String cacheKey = DigestUtils.sha256Hex(apiKey);
        CachedApiKey hit = CACHE.getIfPresent(cacheKey);
        if (Objects.nonNull(hit)) {
            return hit.isRevoked() ? ApiKeyResolveResult.revoked() : ApiKeyResolveResult.active(hit.context());
        }

        CompletableFuture<ApiKeyResolveResult> shared = inFlightLoads.computeIfAbsent(cacheKey, k -> {
            CompletableFuture<ApiKeyResolveResult> future = CompletableFuture.supplyAsync(
                () -> loadAndCache(apiKey, k), VIRTUAL_THREAD_EXECUTOR
            );
            future.whenComplete((_, _) -> inFlightLoads.remove(k, future));
            return future;
        });

        try {
            return shared.join();
        } catch (Exception e) {
            return ApiKeyResolveResult.invalid();
        }
    }

    private ApiKeyResolveResult loadAndCache(String apiKey, String cacheKey) {
        Result<StaticAccessTokenResolveVo> result = loginTokenClient.fetchStaticTokenContext(apiKey);
        if (Objects.isNull(result) || !result.isSuccess()) {
            return ApiKeyResolveResult.invalid();
        }

        StaticAccessTokenResolveVo resolve = result.getData();
        if (Objects.isNull(resolve)) {
            return ApiKeyResolveResult.invalid();
        }

        if (StaticTokenStatus.REVOKED.equals(resolve.getStatus())) {
            CACHE.put(cacheKey, CachedApiKey.revokedMarker());
            return ApiKeyResolveResult.revoked();
        }

        if (!StaticTokenStatus.ACTIVATED.equals(resolve.getStatus())) {
            return ApiKeyResolveResult.invalid();
        }

        StaticAccessTokenContextVo context = resolve.getContext();
        if (Objects.isNull(context)) {
            return ApiKeyResolveResult.invalid();
        }

        CACHE.put(cacheKey, CachedApiKey.active(context));
        return ApiKeyResolveResult.active(context);
    }

    private record CachedApiKey(boolean isRevoked, StaticAccessTokenContextVo context) {

        static CachedApiKey revokedMarker() {
            return new CachedApiKey(true, null);
        }

        static CachedApiKey active(StaticAccessTokenContextVo context) {
            return new CachedApiKey(false, context);
        }
    }
}
