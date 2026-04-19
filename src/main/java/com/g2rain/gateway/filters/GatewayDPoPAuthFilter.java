package com.g2rain.gateway.filters;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Moments;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.ApplicationScope;
import com.g2rain.common.web.DPoPJWTPayload;
import com.g2rain.gateway.exception.GatewayException;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.utils.Constants;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.text.ParseException;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 网关层 DPoP 鉴权过滤器。
 * <p>
 * 负责验证客户端请求中携带的 DPoP Proof（JWT 形式），包括：
 * <ul>
 *     <li>白名单跳过校验</li>
 *     <li>解析并验证 DPoP Proof JWT（头部 typ、JWK、签名）</li>
 *     <li>验证 payload 字段（iat、jti、htm、htu 等）</li>
 *     <li>构建鉴权上下文并写入 {@link EdgePrincipalContext}</li>
 * </ul>
 * 验证失败时抛出 {@link GatewayException}。
 * </p>
 *
 * @author alpha
 * @since 2025/10/6
 */
@Slf4j
@Component
@AllArgsConstructor
public class GatewayDPoPAuthFilter implements HandlerFilterFunction<ServerResponse, ServerResponse>, Ordered {

    /**
     * Micrometer Tracing 核心追踪器
     */
    private final Tracer tracer;

    /**
     * 白名单解析器
     */
    private final WhiteListResolver whiteListResolver;

    /**
     * DPoP 鉴权主流程。
     *
     * <p>
     * 处理顺序：白名单放行 -> 解析并校验 DPoP Header/JWT -> 校验 payload 字段 ->
     * 校验应用授权范围 -> 回填 {@link EdgePrincipalContext} -> 放行后续过滤链。
     * </p>
     *
     * @param request 当前请求
     * @param next    下游处理器
     * @return 下游响应
     * @throws Exception DPoP 校验失败或下游处理异常
     */
    @Override
    public ServerResponse filter(@NonNull ServerRequest request, @NonNull HandlerFunction<ServerResponse> next) throws Exception {
        // 如果命中白名单，则跳过当前 Filter 的处理，直接进入下一个 Filter
        String filterName = this.getClass().getSimpleName();
        if (whiteListResolver.shouldExclude(filterName, request)) {
            return next.handle(request);
        }

        // 解析 DPoP proof
        String jwt = request.headers().firstHeader(Constants.CLIENT_PROOF_HEADER);
        if (Strings.isBlank(jwt)) {
            throw new GatewayException(SystemErrorCode.PARAM_REQUIRED, "DPoP");
        }

        SignedJWT signedJWT;
        try {
            // noinspection ConstantConditions
            signedJWT = SignedJWT.parse(jwt);
        } catch (ParseException e) {
            throw new GatewayException(SystemErrorCode.PARAM_VAL_INVALID, "DPoP Proof");
        }

        // 签名校验
        String hashAlgorithm = verifyHeader(signedJWT);

        // 校验（typ、签名、jti、防重放、htm、htu 等）
        DPoPJWTPayload payload = verifyPayload(request.servletRequest(), signedJWT);

        // 获取上下文
        EdgePrincipalContext context = EdgePrincipalContextHolder.require();

        // 校验应用编码
        boolean isAcdNotAuthorized = Stream
            .ofNullable(context.getApplicationScopes())
            .flatMap(Collection::stream)
            .map(ApplicationScope::getApplicationCode)
            .noneMatch(acd -> Objects.equals(acd, payload.getAcd()));

        if (isAcdNotAuthorized) {
            throw new GatewayException(SystemErrorCode.UNAUTHORIZED, payload.getAcd());
        }

        // 开启 Baggage 作用域（自动写入 MDC）
        try (var _ = tracer.createBaggageInScope(Constants.REQUEST_ID, payload.getJti())) {
            // 构建鉴权上下文 -> 注入上下文
            buildPrincipalContext(context, hashAlgorithm, payload);
            // 继续过滤链
            return next.handle(request);
        }
    }

    /**
     * 校验 JWT Header。
     * <p>
     * 包括 typ 类型、JWK 类型与签名正确性。
     * </p>
     *
     * @param signedJWT 已解析的 SignedJWT
     * @return payload 使用的哈希算法（ph_alg）
     */
    private String verifyHeader(SignedJWT signedJWT) {
        try {
            JWSHeader header = signedJWT.getHeader();

            // 校验 typ
            if (!Constants.CLIENT_PROOF_JWT_TYPE.equalsIgnoreCase(header.getType().toString())) {
                throw new GatewayException(SystemErrorCode.PARAM_VAL_INVALID, "DPoP Proof typ");
            }

            // 校验 JWK 类型
            JWK jwk = header.getJWK();
            if (!(jwk instanceof ECKey ecKey)) {
                throw new GatewayException(SystemErrorCode.PARAM_VAL_INVALID, "DPoP Proof JWK");
            }

            // 校验签名
            JWSVerifier verifier = new ECDSAVerifier(ecKey.toECPublicKey());
            if (!signedJWT.verify(verifier)) {
                throw new GatewayException(SystemErrorCode.PARAM_VAL_INVALID, "DPoP Proof JWS");
            }

            // context参数：在当前的方法体内, 只是为了设置参数hash算法名称
            return header.getCustomParams().getOrDefault("ph_alg", "").toString();
        } catch (JOSEException e) {
            throw new GatewayException(SystemErrorCode.PARAM_VAL_INVALID, "DPoP Proof JWS");
        }
    }

    /**
     * 校验 JWT Payload。
     * <p>
     * 校验 iat、jti、htm、htu 及请求方法匹配性。
     * </p>
     *
     * @param request   当前请求
     * @param signedJWT 已解析的 SignedJWT
     * @return 解析后的 {@link DPoPJWTPayload}
     */
    private DPoPJWTPayload verifyPayload(HttpServletRequest request, SignedJWT signedJWT) {
        try {
            // 校验时间戳
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date iat = claims.getIssueTime();
            Instant now = Instant.now();

            // iat 为空
            if (Objects.isNull(iat)) {
                throw new GatewayException(SystemErrorCode.PARAM_VAL_INVALID, "DPoP Proof iat");
            }

            // iat 过期 || 未来
            Instant timestamp = iat.toInstant();
            if (timestamp.isAfter(now.plusSeconds(30)) || timestamp.isBefore(now.minusSeconds(60))) {
                throw new GatewayException(SystemErrorCode.PARAM_VAL_INVALID, "DPoP Proof iat");
            }

            // 校验 jti
            String jti = claims.getJWTID();
            if (Objects.isNull(jti)) {
                throw new GatewayException(SystemErrorCode.PARAM_VAL_INVALID, "DPoP Proof jti");
            }

            // 校验 htm
            String htm = claims.getStringClaim("htm");
            if (Strings.isBlank(htm)) {
                throw new GatewayException(SystemErrorCode.PARAM_REQUIRED, "DPoP Proof htm");
            }

            // 校验请求方法是否匹配
            if (!htm.equalsIgnoreCase(request.getMethod())) {
                throw new GatewayException(SystemErrorCode.PARAM_VAL_INVALID, "DPoP Proof htm");
            }

            // 校验 htu
            String htu = claims.getStringClaim("htu");
            if (Strings.isBlank(htu)) {
                throw new GatewayException(SystemErrorCode.PARAM_REQUIRED, "DPoP Proof htu");
            }

            if (!htu.equals(request.getRequestURI())) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "DPoP Proof htu");
            }

            // 构建 DPoP JWT payload
            DPoPJWTPayload payload = new DPoPJWTPayload();
            payload.setHtu(htu);
            payload.setHtm(htm);
            payload.setJti(jti);
            payload.setIat(iat.getTime());
            payload.setAcd(claims.getStringClaim("acd"));
            payload.setPha(claims.getStringClaim("pha"));
            return payload;
        } catch (ParseException e) {
            throw new GatewayException(SystemErrorCode.PARAM_VAL_INVALID, "DPoP Proof");
        }
    }

    /**
     * 构建并填充鉴权上下文。
     *
     * @param context       当前请求的鉴权上下文
     * @param hashAlgorithm 哈希算法名称
     * @param payload       DPoP Proof Payload
     */
    private void buildPrincipalContext(EdgePrincipalContext context, String hashAlgorithm, DPoPJWTPayload payload) {
        List<ApplicationScope> scopes = context.getApplicationScopes();
        if (Collections.isEmpty(scopes)) {
            return;
        }

        ApplicationScope scope = scopes.stream().filter(s ->
            Objects.equals(s.getApplicationCode(), payload.getAcd())
        ).findFirst().orElse(null);

        if (Objects.isNull(scope)) {
            return;
        }

        // 通过 micrometer 获取 traceId
        String traceId = Optional.ofNullable(tracer.currentSpan())
            .map(Span::context)
            .map(TraceContext::traceId)
            .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));

        context.setTraceId(traceId);
        context.setHashAlgorithm(hashAlgorithm);
        context.setRequestId(payload.getJti());
        context.setRequestTime(Moments.formatEpochMillis(payload.getIat()));
        context.setParamHashStr(payload.getPha());
        context.setApplicationCode(payload.getAcd());
        // 通过请求所属应用编码换算 应用标识+应用所属应用标识
        context.setApplicationId(scope.getApplicationId());
        context.setApplicationOrganId(scope.getApplicationOrganId());
    }

    /**
     * 定义过滤器执行顺序。
     *
     * @return 执行优先级
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 500;
    }
}
