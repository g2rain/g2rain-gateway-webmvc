package com.g2rain.gateway.route;

import com.g2rain.gateway.matcher.RuleDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterFuncHolderTest {
    private static final List<HttpMessageConverter<?>> MESSAGE_CONVERTERS = List.of();

    @Test
    void shouldPreferExactRouteBeforeVariableRoute() {
        RouterFuncHolder holder = new RouterFuncHolder();
        HandlerFunction<ServerResponse> exactHandler = request -> ServerResponse.ok().build();
        HandlerFunction<ServerResponse> variableHandler = request -> ServerResponse.notFound().build();

        holder.replace(List.of(
            route(1L, "/api/orders/current", "GET", RequestPredicates.method(HttpMethod.GET)
                .and(RequestPredicates.path("/api/orders/current")), exactHandler),
            route(2L, "/api/orders/{id}", "GET", RequestPredicates.method(HttpMethod.GET)
                .and(RequestPredicates.path("/api/orders/{id}")), variableHandler)
        ));

        var matched = holder.route(request(HttpMethod.GET, "/api/orders/current"));
        assertTrue(matched.isPresent());
        assertSame(exactHandler, matched.get());
    }

    @Test
    void shouldMatchVariableRouteWithinBucket() {
        RouterFuncHolder holder = new RouterFuncHolder();
        HandlerFunction<ServerResponse> variableHandler = request -> ServerResponse.ok().build();

        holder.replace(List.of(
            route(1L, "/api/users/{id}", "GET", RequestPredicates.method(HttpMethod.GET)
                .and(RequestPredicates.path("/api/users/{id}")), variableHandler)
        ));

        var matched = holder.route(request(HttpMethod.GET, "/api/users/42"));
        assertTrue(matched.isPresent());
        assertSame(variableHandler, matched.get());
    }

    @Test
    void shouldMatchSingleSegmentWildcardRoute() {
        RouterFuncHolder holder = new RouterFuncHolder();
        HandlerFunction<ServerResponse> wildcardHandler = request -> ServerResponse.ok().build();

        holder.replace(List.of(
            route(1L, "/api/files/*", "GET", RequestPredicates.method(HttpMethod.GET)
                .and(RequestPredicates.path("/api/files/*")), wildcardHandler)
        ));

        var matched = holder.route(request(HttpMethod.GET, "/api/files/demo.txt"));
        assertTrue(matched.isPresent());
        assertSame(wildcardHandler, matched.get());
    }

    @Test
    void shouldSupportIncrementalUpsertAndRemove() {
        RouterFuncHolder holder = new RouterFuncHolder();
        HandlerFunction<ServerResponse> orderHandler = request -> ServerResponse.ok().build();
        HandlerFunction<ServerResponse> userHandler = request -> ServerResponse.accepted().build();

        holder.replace(List.of(
            route(1L, "/api/orders/{id}", "GET", RequestPredicates.method(HttpMethod.GET)
                .and(RequestPredicates.path("/api/orders/{id}")), orderHandler)
        ));

        holder.upsert(route(2L, "/api/users/{id}", "GET", RequestPredicates.method(HttpMethod.GET)
            .and(RequestPredicates.path("/api/users/{id}")), userHandler));
        assertSame(userHandler, holder.route(request(HttpMethod.GET, "/api/users/9")).orElseThrow());

        holder.remove(1L);
        assertTrue(holder.route(request(HttpMethod.GET, "/api/orders/9")).isEmpty());
        assertSame(userHandler, holder.route(request(HttpMethod.GET, "/api/users/9")).orElseThrow());
    }

    private static RuleDefinition<RouterFunction<ServerResponse>> route(
        Long routeId,
        String path,
        String method,
        RequestPredicate predicate,
        HandlerFunction<ServerResponse> handler
    ) {
        RouterFunction<ServerResponse> routerFunction = RouterFunctions.route(predicate, handler);
        return new RuleDefinition<>(routeId, method, path, routerFunction);
    }

    private static ServerRequest request(HttpMethod method, String path, String... headers) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest(method.name(), path);
        servletRequest.setRequestURI(path);
        for (int i = 0; i + 1 < headers.length; i += 2) {
            servletRequest.addHeader(headers[i], headers[i + 1]);
        }
        return ServerRequest.create(servletRequest, MESSAGE_CONVERTERS);
    }
}
