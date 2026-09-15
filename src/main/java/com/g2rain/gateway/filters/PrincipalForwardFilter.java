package com.g2rain.gateway.filters;


import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.PrincipalHeaders;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.utils.Constants;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Principal 转发过滤器。
 * <p>
 * 将 {@link EdgePrincipalContext} 中可透传的身份字段写入请求头，供下游服务消费；
 * 同时移除外部伪造的主体头与敏感认证头（如 Token / DPoP），避免继续向下游泄露。
 * </p>
 *
 * <ul>
 *     <li>先移除全部 {@link PrincipalHeaders}，再按上下文以 set/replace 写入可信值</li>
 *     <li>白名单请求也清除外部主体头，但不注入认证上下文</li>
 *     <li>仅对姓名相关头（{@code name}/{@code organ-name}）做 URL 编码</li>
 *     <li>移除 {@code Authorization}、{@code DPoP} 与调试秘钥头</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/6
 */
@Slf4j
@Component
@AllArgsConstructor
public class PrincipalForwardFilter implements HandlerFilterFunction<ServerResponse, ServerResponse>, Ordered {

    /**
     * {@code DEBUG_KEY} 开启 DEBUG 模式
     */
    private static final String DEBUG_KEY = "zQA730o1RORiKbcR";

    /**
     * {@code whiteListResolver} 用于判断当前请求是否命中白名单规则，
     * 如果命中则可以跳过当前 Filter 的执行。
     * <p>
     * 白名单规则包括全局规则和针对特定 Filter 的规则，匹配顺序为：
     * Filter 白名单 → 全局白名单，
     * 匹配方式包括 contextPath、exactPath、patternPath。
     * </p>
     */
    private final WhiteListResolver whiteListResolver;

    /**
     * Principal 请求头透传入口。
     *
     * <p>
     * 处理顺序：清除外部主体头 ->（非白名单）从 {@link EdgePrincipalContext} 重建可信头
     * -> 移除敏感认证头 -> 继续执行后续链路。
     * </p>
     *
     * @param req  当前请求
     * @param next 下游处理器
     * @return 下游响应
     * @throws Exception 下游处理异常
     */
    @Override
    public ServerResponse filter(@NonNull ServerRequest req, @NonNull HandlerFunction<ServerResponse> next) throws Exception {
        String filterName = this.getClass().getSimpleName();
        ServerRequest.Builder builder = ServerRequest.from(req);
        builder.headers(this::stripPrincipalHeaders);

        if (whiteListResolver.shouldExclude(filterName, req)) {
            return next.handle(builder.build());
        }

        EdgePrincipalContext context = EdgePrincipalContextHolder.require();
        applyTrustedHeaders(context, builder);

        HttpServletRequest request = req.servletRequest();
        String debugKeys = request.getHeader(Constants.DEBUG_KEY_HEADER);
        if (Strings.equals(DEBUG_KEY, debugKeys)) {
            // 须在剥离 / 重建之后写入，避免被 strip 或上下文空值覆盖逻辑干扰
            builder.headers(h -> h.set(PrincipalHeaders.DEBUG.getLower(), Boolean.TRUE.toString()));
        }

        builder.headers(h -> {
            h.remove(Constants.AUTHORIZATION_HEADER);
            h.remove(Constants.CLIENT_PROOF_HEADER);
            h.remove(Constants.DEBUG_KEY_HEADER);
        });

        return next.handle(builder.build());
    }

    /**
     * 移除全部 {@link PrincipalHeaders}（大小写别名），防止外部伪造值与网关重建值并存。
     */
    private void stripPrincipalHeaders(HttpHeaders headers) {
        for (String name : principalHeaderNames()) {
            headers.remove(name);
        }
    }

    /**
     * 按上下文以 set 语义写入可信主体头。
     */
    private void applyTrustedHeaders(EdgePrincipalContext ctx, ServerRequest.Builder builder) {
        for (PrincipalHeaders headerKey : PrincipalHeaders.values()) {
            String value = ctx.getValue(headerKey);
            if (Strings.isBlank(value)) {
                continue;
            }
            String name = headerKey.getLower();
            String encoded = encodeHeaderValue(headerKey, value);
            builder.headers(h -> h.set(name, encoded));
        }
    }

    private static List<String> principalHeaderNames() {
        List<String> names = new ArrayList<>(PrincipalHeaders.values().length * 2);
        for (PrincipalHeaders header : PrincipalHeaders.values()) {
            names.add(header.getLower());
            names.add(header.getUpper());
        }
        return names;
    }

    /**
     * 对 Header 值进行 URL 编码。
     *
     * @param key   Principal Header 键
     * @param value 原始 Header 值
     * @return 编码后的 Header 值
     */
    private String encodeHeaderValue(PrincipalHeaders key, String value) {
        if (!PrincipalHeaders.NAME.equals(key) && !PrincipalHeaders.ORGAN_NAME.equals(key)) {
            return value;
        }

        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("编码请求头部值失败，header:{}, value:{}", key.getLower(), value, e);
            return value;
        }
    }

    /**
     * 获取过滤器执行顺序
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 800;
    }
}
