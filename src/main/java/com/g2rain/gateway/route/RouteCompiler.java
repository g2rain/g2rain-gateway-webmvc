package com.g2rain.gateway.route;


import com.g2rain.common.model.Result;
import com.g2rain.gateway.client.RouteDefinitionClient;
import com.g2rain.gateway.utils.Constants;
import com.g2rain.infra.dto.RouteDefinitionSelectDto;
import com.g2rain.infra.vo.RouteDefinitionVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * 动态路由编译器（Gateway WebMVC 原生路由）
 *
 * <p>
 * 核心职责:
 * <ul>
 *     <li>从路由配置获取 {@link RouteDefinitionVo}</li>
 *     <li>将路由配置转换为 {@link RouterFunction}</li>
 *     <li>原子替换 {@link RouterFuncHolder} 中的路由链</li>
 * </ul>
 *
 * <p>
 * 实现 {@link SmartInitializingSingleton} 接口, 确保 Bean 初始化完成后立即刷新路由
 *
 * @author alpha
 * @since 2026/2/23
 */
@Slf4j
@Component
public class RouteCompiler implements SmartInitializingSingleton {

    /**
     * 持有动态路由的 RouterFuncHolder
     */
    private final RouterFuncHolder holder;

    /**
     * 路由客户端
     */
    private final RouteDefinitionClient routeDefinitionClient;
    private final ObjectProvider<HandlerFilterFunction<ServerResponse, ServerResponse>> routeFilterProvider;

    /**
     * 当前路由快照（消息增删改与初始化刷新共享同一份存储）。
     */
    private volatile Map<Long, RouteDefinitionVo> routeDefinitions = new ConcurrentHashMap<>();

    /**
     * 已按优先级组装好的路由过滤器链（懒加载）。
     */
    private volatile HandlerFilterFunction<ServerResponse, ServerResponse> orderedRouteFilterChain;

    public RouteCompiler(
        RouterFuncHolder holder,
        RouteDefinitionClient routeDefinitionClient,
        ObjectProvider<HandlerFilterFunction<ServerResponse, ServerResponse>> routeFilterProvider
    ) {
        this.holder = holder;
        this.routeDefinitionClient = routeDefinitionClient;
        this.routeFilterProvider = routeFilterProvider;
    }

    /**
     * Bean 初始化完成后回调, 执行路由刷新
     */
    @Override
    public void afterSingletonsInstantiated() {
        refresh();
    }

    /**
     * 刷新路由表
     *
     * <p>
     * 核心流程:
     * <ol>
     *     <li>获取路由配置 {@link RouteDefinitionVo}</li>
     *     <li>过滤无效配置并编译为原生 RouterFunction</li>
     *     <li>按路径具体度排序</li>
     *     <li>原子替换路由链</li>
     * </ol>
     */
    public synchronized void refresh() {
        // 获取路由配置
        Result<List<RouteDefinitionVo>> result = routeDefinitionClient.selectList(
            new RouteDefinitionSelectDto()
        );

        List<RouteDefinitionVo> vos = null;
        if (Objects.nonNull(result)) {
            vos = result.getData();
        }

        if (Objects.isNull(vos)) {
            vos = Collections.emptyList();
        }

        Map<Long, RouteDefinitionVo> newRoutes = vos.stream()
            .filter(Objects::nonNull)
            .filter(v -> Objects.nonNull(v.getId()))
            .collect(Collectors.toConcurrentMap(
                RouteDefinitionVo::getId,
                java.util.function.Function.identity(),
                (_, b) -> b
            ));

        Map<Long, CompiledRoute> newCompiledRoutes = new ConcurrentHashMap<>();
        newRoutes.forEach((routeId, routeDefinition) -> {
            CompiledRoute compiledRoute = compileRoute(routeDefinition);
            if (Objects.nonNull(compiledRoute)) {
                newCompiledRoutes.put(routeId, compiledRoute);
            }
        });

        holder.replace(newCompiledRoutes.values().stream().map(this::toRouteSlot).toList());
        this.routeDefinitions = newRoutes;
        log.info("路由刷新成功，当前活跃路由数: {}", holder.size());
    }

    /**
     * 新增或更新单条路由，并立即生效
     *
     * @param routeDefinition 路由定义
     */
    public synchronized void upsert(RouteDefinitionVo routeDefinition) {
        if (Objects.isNull(routeDefinition) || Objects.isNull(routeDefinition.getId())) {
            log.warn("忽略无效路由 upsert 请求: {}", routeDefinition);
            return;
        }

        CompiledRoute compiledRoute = compileRoute(routeDefinition);
        if (Objects.nonNull(compiledRoute)) {
            holder.upsert(toRouteSlot(compiledRoute));
            routeDefinitions.put(routeDefinition.getId(), routeDefinition);
        } else {
            holder.remove(routeDefinition.getId());
            routeDefinitions.remove(routeDefinition.getId());
        }

        log.info("单条路由更新完成, routeId={}, 当前活跃路由数={}", routeDefinition.getId(), holder.size());
    }

    /**
     * 删除单条路由，并立即生效
     *
     * @param routeId 路由 ID
     */
    public synchronized void remove(Long routeId) {
        if (Objects.isNull(routeId)) {
            return;
        }

        holder.remove(routeId);
        routeDefinitions.remove(routeId);
        log.info("单条路由删除完成, routeId={}, 当前活跃路由数={}", routeId, holder.size());
    }

    /**
     * 对外提供只读快照（用于 OpenAPI 文档索引等读取场景）
     *
     * @return 当前路由快照
     */
    public List<RouteDefinitionVo> getRouteDefinitions() {
        return List.copyOf(routeDefinitions.values());
    }

    private RouterFuncHolder.RouteSlot toRouteSlot(CompiledRoute route) {
        return new RouterFuncHolder.RouteSlot(route.routeId(), route.normalizedPath(), route.routerFunc());
    }

    /**
     * 编译单条路由：产出 Gateway 原生 RouterFunction
     *
     * @param vo 路由定义
     * @return 编译结果；构建失败时返回 null
     */
    private CompiledRoute compileRoute(RouteDefinitionVo vo) {
        try {
            RouterFunction<ServerResponse> fn = toRouterFunction(vo);
            if (Objects.isNull(fn)) {
                return null;
            }

            return new CompiledRoute(fn, normalize(vo), vo.getId());
        } catch (Exception e) {
            log.error("路由编译失败 ID {}: {}", vo.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 根据 RouteDefinitionVo 创建 RouterFunction
     *
     * <p>
     * 核心优化:
     * <ul>
     *     <li>预解析路径匹配规则</li>
     *     <li>预解析方法匹配规则</li>
     *     <li>预创建过滤器实例</li>
     *     <li>校验 URI 合法性</li>
     * </ul>
     *
     * @param vo 路由定义
     * @return RouterFunction<ServerResponse>
     */
    private RouterFunction<ServerResponse> toRouterFunction(RouteDefinitionVo vo) {
        try {
            // 1. 预解析路径匹配规则（保留 Gateway 原生语义，供 fallback 使用）。
            RequestPredicate predicate = GatewayRequestPredicates.path(normalize(vo));

            // 2. 预解析方法匹配规则（保留 Gateway 原生语义，供 fallback 使用）。
            if (StringUtils.hasText(vo.getMethod()) && !"all".equalsIgnoreCase(vo.getMethod())) {
                HttpMethod httpMethod = HttpMethod.valueOf(vo.getMethod().toUpperCase());
                predicate = predicate.and(RequestPredicates.method(httpMethod));
            }

            // 3. 【核心优化】预创建过滤器实例 (Pre-compilation)
            // 在这里调用方法会触发内部解析逻辑（如正则编译、URI 校验），请求进来时直接引用结果
            // 注意：BeforeFilterFunctions 返回的是 Gateway 内部类型，不要强转为 UnaryOperator，
            // 否则会因类加载器不同触发 ClassCastException（app loader vs bootstrap loader）
            Map<String, String> routeHeaders = parseRouteHeaders(vo.getHeaderParameters());
            UnaryOperator<ServerRequest> enhancement = withRequestEnhancement(vo.getContext());
            var routeBuilder = GatewayRouterFunctions.route(String.valueOf(vo.getId()))
                .route(predicate, HandlerFunctions.http())
                .before(enhancement)
                .before(BeforeFilterFunctions.uri(URI.create(vo.getEndpointHost())))
                .before(BeforeFilterFunctions.stripPrefix(1));

            for (Map.Entry<String, String> headerEntry : routeHeaders.entrySet()) {
                routeBuilder = routeBuilder.before(BeforeFilterFunctions.addRequestHeader(headerEntry.getKey(), headerEntry.getValue()));
            }

            HandlerFilterFunction<ServerResponse, ServerResponse> routeFilterChain = getOrderedRouteFilterChain();
            if (Objects.nonNull(routeFilterChain)) {
                routeBuilder = routeBuilder.filter(routeFilterChain);
            }

            // 条件路径重写：仅当配置了 endpointPath 且非全局通配时添加
            if (StringUtils.hasText(vo.getEndpointPath()) && !"/**".equals(vo.getPath())) {
                routeBuilder = routeBuilder.before(BeforeFilterFunctions.rewritePath(
                    vo.getPath(), vo.getEndpointPath()
                ));
            }

            return routeBuilder.build();
        } catch (Exception e) {
            // 【关键容错】捕获非法 URI 或配置错误，确保单条路由故障不影响整个路由表的刷新
            log.error("路由编译失败，跳过该条配置 [ID: {}, Path: {}]: {}", vo.getId(), vo.getPath(), e.getMessage());
            return null;
        }
    }

    /**
     * 预创建请求增强函数
     *
     * <p>
     * 注入 route 上下文
     *
     * @param context 路由上下文
     * @return UnaryOperator<ServerRequest>
     */
    private UnaryOperator<ServerRequest> withRequestEnhancement(String context) {
        return request -> {
            // 注入 route 上下文（仅写入 HttpServletRequest，作为白名单匹配唯一来源）
            request.servletRequest().setAttribute(Constants.ROUTE_CONTEXT_PATH, context);
            return request;
        };
    }

    private Map<String, String> parseRouteHeaders(String headerParameters) {
        Map<String, String> headerMap = new HashMap<>();
        if (!StringUtils.hasText(headerParameters)) {
            return headerMap;
        }
        for (String entry : headerParameters.split(";")) {
            String[] kv = entry.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            headerMap.put(key, kv[1].trim());
        }
        return headerMap;
    }

    /**
     * 规范化路径
     *
     * @param vo 路由定义
     * @return 标准化路径字符串
     */
    private String normalize(RouteDefinitionVo vo) {
        return ("/" + vo.getContext() + "/" + vo.getPath()).replaceAll("/+", "/");
    }

    /**
     * 懒加载并缓存按优先级组装后的 HandlerFilterFunction 链。
     */
    private HandlerFilterFunction<ServerResponse, ServerResponse> getOrderedRouteFilterChain() {
        HandlerFilterFunction<ServerResponse, ServerResponse> chain = orderedRouteFilterChain;
        if (Objects.nonNull(chain)) {
            return chain;
        }

        synchronized (this) {
            if (Objects.nonNull(orderedRouteFilterChain)) {
                return orderedRouteFilterChain;
            }

            List<HandlerFilterFunction<ServerResponse, ServerResponse>> filters = routeFilterProvider.orderedStream().toList();
            if (filters.isEmpty()) {
                return null;
            }

            orderedRouteFilterChain = composeFilterChain(filters);
            log.info("安装路由过滤器链成功，filters={}, orders={}", filters.size(), filters.stream().map(this::orderOf).toList());
            return orderedRouteFilterChain;
        }
    }

    /**
     * 按升序优先级组合过滤器链：order 值越小越先执行。
     */
    private HandlerFilterFunction<ServerResponse, ServerResponse> composeFilterChain(
        List<HandlerFilterFunction<ServerResponse, ServerResponse>> orderedFilters) {
        return (request, next) -> {
            HandlerFunction<ServerResponse> chained = next;
            for (int i = orderedFilters.size() - 1; i >= 0; i--) {
                HandlerFilterFunction<ServerResponse, ServerResponse> current = orderedFilters.get(i);
                HandlerFunction<ServerResponse> downstream = chained;
                chained = req -> current.filter(req, downstream);
            }

            return chained.handle(request);
        };
    }

    private int orderOf(HandlerFilterFunction<ServerResponse, ServerResponse> filter) {
        if (filter instanceof Ordered ordered) {
            return ordered.getOrder();
        }

        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * 路由编译产物：原生 RouterFunction
     */
    private record CompiledRoute(RouterFunction<ServerResponse> routerFunc, String normalizedPath, Long routeId) {
    }
}
