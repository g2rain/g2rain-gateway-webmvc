/**
 * Gateway WebMVC 过滤器与响应处理器。
 *
 * <p>
 * 本包同时包含两类组件：
 * </p>
 * <ul>
 *     <li>Servlet 过滤器（继承 {@code OncePerRequestFilter}）：负责上下文作用域、异常兜底、请求/响应体缓存</li>
 *     <li>Route 过滤器（实现 {@code HandlerFilterFunction}）与响应处理器（{@link com.g2rain.gateway.filters.ResponseBodyProcessor}）：
 *         负责鉴权、权限校验、签名校验、请求日志、头透传与响应改写</li>
 * </ul>
 *
 * <p>
 * 整体形成“Servlet 外层过滤 + 路由内过滤 + 响应后处理”的三段式链路。
 * </p>
 *
 * <h2>核心过滤器</h2>
 * <ul>
 *     <li><b>EdgePrincipalContextScopeFilter</b> — 创建并绑定请求级 Principal 上下文；优先级 {@code 0}</li>
 *     <li><b>GlobalErrorFilter</b> — 全局异常兜底并输出统一 JSON；优先级 {@code 100}</li>
 *     <li><b>CachedBodyFilter</b> — 缓存请求体与响应体，并调度 {@code ResponseBodyProcessor}；优先级 {@code 200}</li>
 *     <li><b>TraceLoggingFilter</b> — 路由内请求日志 + 响应后日志；优先级 {@code 300}</li>
 *     <li><b>GatewayTokenAuthFilter</b> — Token JWT 鉴权并填充上下文；优先级 {@code 400}</li>
 *     <li><b>GatewayDPoPAuthFilter</b> — DPoP Proof 校验并补充签名相关上下文；优先级 {@code 500}</li>
 *     <li><b>ApiPermissionFilter</b> — 接口权限校验（当前默认未注册为 Bean）；优先级 {@code 600}</li>
 *     <li><b>SignVerificationFilter</b> — 按上下文算法校验 query+body 摘要；优先级 {@code 700}</li>
 *     <li><b>PrincipalForwardFilter</b> — 转发 Principal 头并移除敏感认证头；优先级 {@code 800}</li>
 *     <li><b>ResponseAdjustFilter</b> — 成功响应字段补全（如 id->name）；优先级 {@code 900}</li>
 * </ul>
 *
 * <h2>典型执行顺序</h2>
 * <ol>
 *     <li>{@link com.g2rain.gateway.filters.EdgePrincipalContextScopeFilter} — 创建请求级上下文，优先级 {@code 0}</li>
 *     <li>{@link com.g2rain.gateway.filters.GlobalErrorFilter} — 兜底异常转换，优先级 {@code 100}</li>
 *     <li>{@link com.g2rain.gateway.filters.CachedBodyFilter} — 缓存请求/响应体，优先级 {@code 200}</li>
 *     <li>{@link com.g2rain.gateway.filters.TraceLoggingFilter} — 请求/响应日志记录，优先级 {@code 300}</li>
 *     <li>{@link com.g2rain.gateway.filters.GatewayTokenAuthFilter} — Token JWT 鉴权，优先级 {@code 400}</li>
 *     <li>{@link com.g2rain.gateway.filters.GatewayDPoPAuthFilter} — DPoP Proof 鉴权，优先级 {@code 500}</li>
 *     <li>{@link com.g2rain.gateway.filters.ApiPermissionFilter} — 接口权限校验，优先级 {@code 600}</li>
 *     <li>{@link com.g2rain.gateway.filters.SignVerificationFilter} — 签名验证，优先级 {@code 700}</li>
 *     <li>{@link com.g2rain.gateway.filters.PrincipalForwardFilter} — Principal 转发，优先级 {@code 800}</li>
 *     <li>{@link com.g2rain.gateway.filters.ResponseAdjustFilter} — 响应调整，优先级 {@code 900}</li>
 * </ol>
 *
 * <h2>注意事项</h2>
 * <ul>
 *     <li>{@link com.g2rain.gateway.filters.CachedBodyFilter} 必须在其他需要读取请求体的过滤器之前执行。</li>
 *     <li>Token / DPoP 鉴权必须在签名校验和 Principal 转发之前完成。</li>
 *     <li>DPoP Proof 鉴权依赖请求携带 DPoP JWT，必须在签名验证之前完成。</li>
 *     <li>{@link com.g2rain.gateway.filters.PrincipalForwardFilter} 会移除敏感认证头，确保下游服务无法直接获取 Token 或 DPoP。</li>
 *     <li>TraceLoggingFilter 会增加 I/O 开销，可根据实际情况调整优先级或日志级别。</li>
 *     <li>SignVerificationFilter 依赖 {@link com.g2rain.gateway.model.context.EdgePrincipalContext} 中的签名信息，必须在鉴权之后执行。</li>
 *     <li>ResponseAdjustFilter 仅处理 JSON 响应，必须配合 {@link com.g2rain.gateway.filters.CachedBodyFilter} 使用以缓存响应体。</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/6
 */
package com.g2rain.gateway.filters;
