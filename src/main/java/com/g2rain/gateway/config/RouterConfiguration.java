package com.g2rain.gateway.config;


import com.g2rain.gateway.route.RouterFuncHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;


/**
 * 路由函数注册配置类.
 *
 * <p>
 * 在 Spring Boot 4 + Spring MVC.fn 模式下,
 * 所有 {@link RouterFunction} Bean 会被 {@code RouterFunctionMapping} 自动收集,
 * 并在请求到达 DispatcherServlet 时参与路由匹配.
 *
 * <p>
 * 本类的核心作用:
 * <ul>
 *     <li>将自定义的 RouterFuncHolder 暴露为一个 RouterFunction Bean</li>
 *     <li>使整个动态路由体系接入 Spring MVC 请求处理流程</li>
 * </ul>
 *
 * <p>
 * 等价逻辑:
 * DispatcherServlet → RouterFunctionMapping → holder::route
 *
 * <p>
 * 这里返回的是一个函数式接口的 method reference,
 * 实际执行时会调用 {@link RouterFuncHolder#route}
 *
 * @author alpha
 * @since 2026/2/23
 */
@Configuration
public class RouterConfiguration {

    /**
     * 注册 RouterFunction Bean
     *
     * <p>
     * 方法引用 holder::route 会被 Spring MVC.fn 适配为 RouterFunction,
     * 所有请求进入时会调用 holder 内部逻辑进行匹配
     *
     * @param holder 动态路由持有器
     * @return RouterFunction<ServerResponse>
     */
    @Bean
    public RouterFunction<ServerResponse> routerFunction(RouterFuncHolder holder) {
        // 直接返回方法引用, Spring 会在每次请求调用时执行 holder.route(request)
        return holder::route;
    }
}
