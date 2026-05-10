package com.g2rain.gateway.client;


import com.g2rain.basis.api.ResourceApiApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author alpha
 * @since 2026/3/15
 */
@FeignClient(name = "g2rain-basis", contextId = "routeDefinitionClient", path = "/resource_api")
public interface RouteDefinitionClient extends ResourceApiApi {
}
