package com.g2rain.gateway.cache;

import com.g2rain.basis.vo.RouteDefinitionVo;
import com.g2rain.gateway.route.GatewayRouteLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RouteSyncTest {
    private final GatewayRouteLoader gatewayRouteLoader = mock(GatewayRouteLoader.class);
    private final RouteSync routeSync = new RouteSync(gatewayRouteLoader);

    @AfterEach
    void tearDown() {
        routeSync.destroy();
    }

    @Test
    void shouldUseUpsertWhenCreateWindowContainsSingleRoute() throws InterruptedException {
        RouteDefinitionVo route = route(1L, "/user/profile");

        routeSync.create(route.getId(), route);

        Thread.sleep(1200L);

        verify(gatewayRouteLoader).upsert(route);
        HashMap<Long, RouteDefinitionVo> map = new HashMap<>();
        map.put(1L, route);
        verify(gatewayRouteLoader, never()).refresh(map);
    }

    @Test
    void shouldUseRefreshWhenCreateWindowContainsMultipleRoutes() throws InterruptedException {
        RouteDefinitionVo first = route(1L, "/user/profile");
        RouteDefinitionVo second = route(2L, "/order/detail");

        routeSync.create(first.getId(), first);
        routeSync.create(second.getId(), second);

        Thread.sleep(1200L);
        HashMap<Long, RouteDefinitionVo> map = new HashMap<>();
        map.put(1L, first);
        map.put(2L, second);
        verify(gatewayRouteLoader).refresh(map);
        verify(gatewayRouteLoader, never()).upsert(any(RouteDefinitionVo.class));
    }

    private static RouteDefinitionVo route(Long id, String path) {
        RouteDefinitionVo route = new RouteDefinitionVo();
        route.setId(id);
        route.setMethod("GET");
        route.setPath(path);
        route.setRoutePrefix("basis");
        route.setEndpoint("lb://g2rain-basis");
        route.setName("test-" + id);
        return route;
    }
}
