/**
 * <h1>动态路由模块（Gateway WebMVC 原生版）</h1>
 *
 * <p>
 * 本模块负责在 Spring Boot 4 + Spring MVC.fn 下提供动态路由能力，
 * 路由匹配与转发完全使用 Spring Cloud Gateway WebMVC 原生机制：
 * </p>
 *
 * <ul>
 *     <li>路由匹配由 {@link org.springframework.web.servlet.function.RouterFunction} 原生执行</li>
 *     <li>route 包负责路由业务组装与热更新触发</li>
 * </ul>
 *
 * <h2>模块核心类职责</h2>
 *
 * <ul>
 *     <li>{@link com.g2rain.gateway.config.RouterConfiguration}：
 *         注册 RouterFunction Bean，使 Spring MVC 请求能够路由到动态路由系统</li>
 *     <li>{@link com.g2rain.gateway.route.RouterFuncHolder}：
 *         按“路径具体度 + routeId”维护匹配顺序，并在请求期顺序匹配</li>
 *     <li>{@link com.g2rain.gateway.route.RouteCompiler}：
 *         将路由配置编译为 RouterFunction，支持全量刷新与单条 upsert/remove 热更新</li>
 * </ul>
 *
 * <h2>请求匹配流程</h2>
 *
 * <ol>
 *     <li>请求到达 {@code DispatcherServlet}</li>
 *     <li>Spring MVC.fn 调用 RouterFunction Bean，即 {@code RouterFuncHolder.route(request)}</li>
 *     <li>holder 顺序执行原生 RouterFunction 匹配，命中后返回 HandlerFunction</li>
 * </ol>
 *
 * <h2>路由刷新流程</h2>
 *
 * <ol>
 *     <li>{@code RouteCompiler.refresh()} 被触发（初始化或后台刷新）</li>
 *     <li>获取路由配置 {@code RouteDefinitionVo}，转换为 RouterFunction</li>
 *     <li>原子替换 RouterFuncHolder 运行时快照（路由链）</li>
 * </ol>
 *
 * <h2>运行期增量更新</h2>
 *
 * <p>
 * 除全量刷新外，模块还支持消息驱动的单条路由更新：
 * {@code RouteCompiler.upsert()} 与 {@code RouteCompiler.remove()} 直接修改
 * {@code RouterFuncHolder} 当前快照，实现无重启生效。
 * </p>
 *
 * @author alpha
 * @since 2025/9/27
 */
package com.g2rain.gateway.route;

