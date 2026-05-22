/**
 * g2rain 网关（Spring WebMVC 函数式路由）过滤器与响应后处理包。
 *
 * <p>
 * 实现形态分为两层：
 * </p>
 * <ul>
 *     <li><b>Servlet 过滤器</b>（{@link org.springframework.web.filter.OncePerRequestFilter}）—
 *         在 DispatcherServlet 外围建立上下文、兜底异常、缓存 body。</li>
 *     <li><b>路由过滤器</b>（{@link org.springframework.web.servlet.function.HandlerFilterFunction}）—
 *         由 {@link com.g2rain.gateway.route.GatewayRouteLoader} 聚合所有 Bean 后按
 *         {@link org.springframework.core.Ordered} 串联，完成鉴权、权限、签名、头转发等。</li>
 *     <li><b>响应后处理</b>（{@link com.g2rain.gateway.filters.ResponseBodyProcessor}）—
 *         在 {@link com.g2rain.gateway.filters.CachedBodyFilter} 写回响应前，对已缓存的 JSON body 二次处理。</li>
 * </ul>
 *
 * <h2>Servlet 层（执行顺序）</h2>
 * <ol>
 *     <li>{@link com.g2rain.gateway.filters.EdgePrincipalContextScopeFilter}（+0）— 绑定
 *         {@link com.g2rain.gateway.model.context.EdgePrincipalContext}。</li>
 *     <li>{@link com.g2rain.gateway.filters.GlobalErrorFilter}（+100）— 捕获未处理异常并输出统一 JSON。</li>
 *     <li>{@link com.g2rain.gateway.filters.CachedBodyFilter}（+200）— 包装
 *         {@link com.g2rain.gateway.model.web.CachedBodyRequest} /
 *         {@link com.g2rain.gateway.model.web.CachedBodyResponse}，并调度
 *         {@link com.g2rain.gateway.filters.ResponseBodyProcessor}。</li>
 * </ol>
 *
 * <h2>路由层（执行顺序）</h2>
 * <p>顺序值为 {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE} 加偏移量。</p>
 * <ol>
 *     <li>{@link com.g2rain.gateway.filters.TraceLoggingFilter}（+300）— 请求侧日志；响应侧日志由其
 *         实现的 {@link com.g2rain.gateway.filters.ResponseBodyProcessor} 完成。</li>
 *     <li>{@link com.g2rain.gateway.filters.ApiKeyFilter}（+350）— 静态 API Key 鉴权，见下文。</li>
 *     <li>{@link com.g2rain.gateway.filters.GatewayTokenAuthFilter}（+400）— JWT 鉴权。</li>
 *     <li>{@link com.g2rain.gateway.filters.GatewayDPoPAuthFilter}（+500）— DPoP Proof 鉴权。</li>
 *     <li>{@link com.g2rain.gateway.filters.ApiPermissionFilter}（+600）— 按路由 ID 校验接口权限。</li>
 *     <li>{@link com.g2rain.gateway.filters.SignVerificationFilter}（+700）— query + body 摘要校验。</li>
 *     <li>{@link com.g2rain.gateway.filters.PrincipalForwardFilter}（+800）— Principal 头转发并剥离敏感认证头。</li>
 *     <li>{@link com.g2rain.gateway.filters.ResponseAdjustFilter}（+900）— JSON 响应字段补全（
 *         {@link com.g2rain.gateway.filters.ResponseBodyProcessor}）。</li>
 * </ol>
 *
 * <h2>静态 API Key 鉴权</h2>
 * <p>
 * 客户端使用 {@code Authorization: Bearer sk-...}（格式见 {@link com.g2rain.gateway.utils.AuthScheme}）。
 * {@link com.g2rain.gateway.filters.ApiKeyFilter} 经 {@link com.g2rain.gateway.cache.ApiKeyCache} 调用
 * {@link com.g2rain.gateway.client.LoginTokenClient}（Feign，basis-api）解析令牌；
 * 成功后设置 {@code staticTokenAuthenticated}，后续 JWT / DPoP / 签名校验过滤器跳过。
 * </p>
 *
 * <h2>运行约束</h2>
 * <ul>
 *     <li>{@link com.g2rain.gateway.filters.CachedBodyFilter} 须先于依赖 body 的路由过滤器。</li>
 *     <li>JWT / DPoP 须在 {@link com.g2rain.gateway.filters.SignVerificationFilter} 之前完成。</li>
 *     <li>{@link com.g2rain.gateway.filters.ResponseAdjustFilter} 须在响应体已缓存后执行。</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/6
 */
package com.g2rain.gateway.filters;
