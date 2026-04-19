package com.g2rain.gateway.route;


import com.g2rain.common.utils.Collections;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * 动态路由持有器（Gateway WebMVC 原生路由）
 *
 * <p>
 * 核心作用:
 * <ul>
 *     <li>持有当前生效的 {@link RouterFunction} 链路</li>
 *     <li>请求到达时按顺序调用 RouterFunction 完成原生路由匹配</li>
 *     <li>路由刷新时原子替换整个路由快照</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/2/23
 */
@Component
public class RouterFuncHolder {
    private static final Comparator<RouteSlot> ROUTE_ORDER = Comparator
        .comparingInt((RouteSlot route) -> wildcardScore(route.normalizedPath()))
        .thenComparing((RouteSlot route) -> route.normalizedPath().length(), Comparator.reverseOrder())
        .thenComparing(route -> route.routeId() == null ? Long.MAX_VALUE : route.routeId());

    /**
     * 运行时路由索引：按 routeId 存放，支持单条增删改
     */
    private final Map<Long, RouteSlot> routesById = new ConcurrentHashMap<>();

    /**
     * 运行时匹配顺序：按路径具体度 + routeId 排序
     */
    private final NavigableSet<RouteSlot> orderedRoutes = new ConcurrentSkipListSet<>(ROUTE_ORDER);

    /**
     * 核心路由匹配方法
     *
     * <p>
     * 匹配逻辑：顺序执行 RouterFunction，命中即返回对应 HandlerFunction
     *
     * @param request ServerRequest 对象
     * @return Optional 包装 HandlerFunction
     */
    public Optional<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        for (RouteSlot route : orderedRoutes) {
            Optional<HandlerFunction<ServerResponse>> matched = route.routerFunction().route(request);
            if (matched.isPresent()) {
                return matched;
            }
        }

        return Optional.empty();
    }

    /**
     * 原子替换生效路由链
     *
     * @param routes 新路由快照
     */
    public synchronized void replace(Collection<RouteSlot> routes) {
        routesById.clear();
        orderedRoutes.clear();
        if (Collections.isEmpty(routes)) {
            return;
        }

        for (RouteSlot route : routes) {
            if (Objects.isNull(route) || Objects.isNull(route.routeId())) {
                continue;
            }

            routesById.put(route.routeId(), route);
            orderedRoutes.add(route);
        }
    }

    /**
     * 新增或更新单条路由
     *
     * @param route 路由定义
     */
    public synchronized void upsert(RouteSlot route) {
        if (Objects.isNull(route) || Objects.isNull(route.routeId())) {
            return;
        }

        RouteSlot previous = routesById.put(route.routeId(), route);
        if (Objects.nonNull(previous)) {
            orderedRoutes.remove(previous);
        }

        orderedRoutes.add(route);
    }

    /**
     * 删除单条路由
     *
     * @param routeId 路由 ID
     */
    public synchronized void remove(Long routeId) {
        if (Objects.isNull(routeId)) {
            return;
        }

        RouteSlot previous = routesById.remove(routeId);
        if (Objects.isNull(previous)) {
            return;
        }

        orderedRoutes.remove(previous);
    }

    public int size() {
        return orderedRoutes.size();
    }

    private static int wildcardScore(String path) {
        int score = 0;
        for (int i = 0, j = path.length(); i < j; i++) {
            char ch = path.charAt(i);
            if (ch == '*' || ch == '{') {
                score++;
            }
        }

        return score;
    }

    public record RouteSlot(Long routeId, String normalizedPath, RouterFunction<ServerResponse> routerFunction) {
    }
}
