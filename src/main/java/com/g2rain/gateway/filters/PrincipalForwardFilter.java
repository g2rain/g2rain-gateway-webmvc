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
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Principal 转发过滤器。
 * <p>
 * 将 {@link EdgePrincipalContext} 中可透传的身份字段写入请求头，供下游服务消费；
 * 同时移除敏感认证头（如 Token / DPoP）避免继续向下游泄露。
 * </p>
 *
 * <ul>
 *     <li>按 {@link PrincipalHeaders} 将上下文信息写入请求头</li>
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
     * 处理顺序：白名单放行 -> 从 {@link EdgePrincipalContext} 读取身份字段 ->
     * 注入请求头（必要字段做 URL 编码）-> 移除敏感认证头 -> 继续执行后续链路。
     * </p>
     *
     * @param req  当前请求
     * @param next 下游处理器
     * @return 下游响应
     * @throws Exception 下游处理异常
     */
    @Override
    public ServerResponse filter(@NonNull ServerRequest req, @NonNull HandlerFunction<ServerResponse> next) throws Exception {
        // 获取当前过滤器的类名（用于白名单判断）
        String filterName = this.getClass().getSimpleName();

        // 判断当前请求是否命中白名单规则
        // 命中则跳过本过滤器，直接进入下一个过滤器
        if (whiteListResolver.shouldExclude(filterName, req)) {
            return next.handle(req);
        }

        // 获取上下文
        EdgePrincipalContext context = EdgePrincipalContextHolder.require();
        ServerRequest.Builder builder = ServerRequest.from(req);

        HttpServletRequest request = req.servletRequest();
        String debugKeys = request.getHeader(Constants.DEBUG_KEY_HEADER);
        if (Strings.equals(DEBUG_KEY, debugKeys)) {
            builder.headers(h -> h.add(PrincipalHeaders.DEBUG.getLower(), Boolean.TRUE.toString()));
        }

        // 基于上下文信息动态添加或移除 Header（仅写入 ServerRequest 视图）
        applyHeaders(context, (name, value) -> builder.headers(h -> h.add(name, value)),
            names -> builder.headers(h -> names.forEach(h::remove))
        );

        // 继续执行下一个过滤器
        return next.handle(builder.build());
    }

    /**
     * 根据 Principal 上下文对请求头进行处理：添加需要转发的 Principal Headers，
     * 并移除敏感认证头。
     *
     * @param ctx     当前的 Principal 上下文，提供 header 值
     * @param adder   添加 header 的回调函数，接受 header 名称和值
     * @param remover 移除 header 的回调函数，接受要移除的 header 名称列表
     */
    private void applyHeaders(EdgePrincipalContext ctx, BiConsumer<String, String> adder, Consumer<List<String>> remover) {
        // 遍历所有定义的 PrincipalHeaders
        for (PrincipalHeaders headerKey : PrincipalHeaders.values()) {
            String value = ctx.getValue(headerKey);
            // 如果值为空则跳过
            if (Strings.isBlank(value)) {
                continue;
            }

            // 对 header 值进行 URL 编码并添加
            adder.accept(headerKey.getLower(), encodeHeaderValue(headerKey, value));
        }

        // 移除敏感认证头
        remover.accept(List.of(Constants.AUTHORIZATION_HEADER, Constants.CLIENT_PROOF_HEADER, Constants.DEBUG_KEY_HEADER));
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
