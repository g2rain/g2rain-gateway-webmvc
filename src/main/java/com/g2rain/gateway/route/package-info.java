/**
 * 网关动态路由编排包（Spring Cloud Gateway Server MVC / WebMVC.fn）。
 *
 * <p>与 WebFlux 版不同，本栈将每条业务路由编译为一条可执行的
 * {@link org.springframework.web.servlet.function.RouterFunction}（响应类型为 {@link org.springframework.web.servlet.function.ServerResponse}），
 * 再通过 {@link com.g2rain.gateway.matcher.MatchEngine} 在请求期做「方法 + 路径」粗筛，
 * 命中后对具体 {@link org.springframework.web.servlet.function.RouterFunction} 调用
 * {@link org.springframework.web.servlet.function.RouterFunction#route(org.springframework.web.servlet.function.ServerRequest)}
 * 做细判，从而把控制面配置接入标准 MVC 函数式路由模型。</p>
 *
 * <h2>核心职责</h2>
 * <ul>
 *     <li>从 Basis 控制面（Feign 客户端）拉取 {@link com.g2rain.basis.vo.RouteDefinitionVo} 与注册信息；</li>
 *     <li>在 {@link com.g2rain.gateway.route.GatewayRouteLoader} 中把 VO 编译为 Gateway WebMVC 的 route 构建器产物；</li>
 *     <li>在 {@link com.g2rain.gateway.route.RouterFuncHolder} 中维护「路由 id → 编译结果」与匹配引擎快照；</li>
 *     <li>支持全量刷新与单条 upsert/remove，并打日志便于运维观测。</li>
 * </ul>
 *
 * <h2>主要类型</h2>
 * <ul>
 *     <li>{@link com.g2rain.gateway.route.GatewayRouteLoader}：容器就绪后拉全量配置、编译并替换 {@link com.g2rain.gateway.route.RouterFuncHolder}；</li>
 *     <li>{@link com.g2rain.gateway.route.RouterFuncHolder}：请求期 {@link com.g2rain.gateway.route.RouterFuncHolder#route(org.springframework.web.servlet.function.ServerRequest)} 暴露给上层入口。</li>
 * </ul>
 *
 * <h2>请求路径（概念）</h2>
 * <p>请求进入 MVC.fn 总路由 → {@code RouterFuncHolder.route}：先用 {@link com.g2rain.gateway.matcher.MatchEngine}
 * 按方法与路径选出候选 {@link com.g2rain.gateway.matcher.MatchRule}，再对 {@code rule.target()}（即
 * {@code RouterFunction}）执行 route 匹配；命中后可将内部路由 id 写入 request attribute 供后续过滤器使用。</p>
 *
 * @author alpha
 * @since 2026/2/23
 */
package com.g2rain.gateway.route;
