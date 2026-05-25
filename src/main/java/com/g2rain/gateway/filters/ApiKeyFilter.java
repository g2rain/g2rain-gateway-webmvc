package com.g2rain.gateway.filters;


import com.g2rain.common.utils.Moments;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.cache.ApiKeyCache;
import com.g2rain.gateway.enums.ApiKeyResolveOutcome;
import com.g2rain.gateway.enums.GatewayErrorCode;
import com.g2rain.gateway.exception.GatewayException;
import com.g2rain.basis.vo.StaticAccessTokenContextVo;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.utils.AuthScheme;
import com.g2rain.gateway.utils.Constants;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 静态 API Key 鉴权（{@link HandlerFilterFunction}）。
 *
 * <p>
 * 识别 {@link AuthScheme#isApiKey(String)} 后经 {@link ApiKeyCache} 回源 basis，
 * 将 {@link StaticAccessTokenContextVo} 映射进 {@link EdgePrincipalContext} 并置 {@code staticTokenAuthenticated=true}，
 * 使后续 JWT / DPoP / 签名校验过滤器跳过。
 * </p>
 *
 * <p>顺序：{@link Ordered#HIGHEST_PRECEDENCE} + 350。</p>
 *
 * @author alpha
 * @since 2026/5/22
 */
@Component
@AllArgsConstructor
public class ApiKeyFilter implements HandlerFilterFunction<ServerResponse, ServerResponse>, Ordered {

    private final WhiteListResolver whiteListResolver;

    private final ApiKeyCache apiKeyCache;

    /**
     * Micrometer Tracing 核心追踪器
     */
    private final Tracer tracer;

    @Override
    public ServerResponse filter(@NonNull ServerRequest request, @NonNull HandlerFunction<ServerResponse> next) throws Exception {
        String filterName = getClass().getSimpleName();
        if (whiteListResolver.shouldExclude(filterName, request)) {
            return next.handle(request);
        }

        String authHeader = request.headers().firstHeader(Constants.AUTHORIZATION_HEADER);
        if (Strings.isBlank(authHeader)) {
            return next.handle(request);
        }

        String credential = AuthScheme.credential(authHeader);
        if (Objects.isNull(credential) || !AuthScheme.isApiKey(credential)) {
            return next.handle(request);
        }

        var result = apiKeyCache.resolve(credential);
        if (result.outcome() == ApiKeyResolveOutcome.INVALID) {
            throw new GatewayException(GatewayErrorCode.API_KEY_INVALID, "apiKey");
        }
        if (result.outcome() == ApiKeyResolveOutcome.REVOKED) {
            throw new GatewayException(GatewayErrorCode.API_KEY_REVOKED, "apiKey");
        }

        applyContext(EdgePrincipalContextHolder.require(), result.context(), credential);
        return next.handle(request);
    }

    private void applyContext(EdgePrincipalContext principal, StaticAccessTokenContextVo ctx, String apiKey) {
        // 通过 micrometer 获取 traceId
        String traceId = Optional.ofNullable(tracer.currentSpan())
            .map(Span::context).map(TraceContext::traceId)
            .orElseGet(() ->
                UUID.randomUUID().toString().replace("-", "")
            );

        principal.setApiKey(apiKey);
        principal.setStaticTokenAuthenticated(true);
        principal.setTraceId(traceId);
        principal.setRequestId(UUID.randomUUID().toString());
        principal.setRequestTime(Moments.format(Moments.now()));
        principal.setSessionType(ctx.getSessionType());
        principal.setPassportId(ctx.getPassportId());
        principal.setUserId(ctx.getUserId());
        principal.setName(ctx.getName());
        principal.setAdminUser(ctx.isAdminUser());
        principal.setOrganType(ctx.getOrganType());
        principal.setOrganId(ctx.getOrganId());
        principal.setOrganName(ctx.getOrganName());
        principal.setAdminCompany(ctx.isAdminCompany());
        principal.setApplicationId(ctx.getApplicationId());
        principal.setApplicationCode(ctx.getApplicationCode());
        principal.setApplicationOrganId(ctx.getApplicationOrganId());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 350;
    }
}
