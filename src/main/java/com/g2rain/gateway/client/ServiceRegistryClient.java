package com.g2rain.gateway.client;


import com.g2rain.basis.api.ServiceRegistryApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author alpha
 * @since 2026/4/28
 */
@FeignClient(name = "g2rain-basis", contextId = "serviceRegistryClient", path = "/service_registry")
public interface ServiceRegistryClient extends ServiceRegistryApi {
}
