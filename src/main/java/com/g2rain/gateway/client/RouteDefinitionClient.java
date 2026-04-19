package com.g2rain.gateway.client;


import com.g2rain.infra.api.RouteDefinitionApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author alpha
 * @since 2026/3/15
 */
@FeignClient(name = "g2rain-infra", contextId = "routeDefinitionClient", path = "/route_definition")
public interface RouteDefinitionClient extends RouteDefinitionApi {
}
