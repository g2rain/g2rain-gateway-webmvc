package com.g2rain.gateway.client;


import com.g2rain.basis.api.OrganApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author alpha
 * @since 2026/4/13
 */
@FeignClient(name = "g2rain-basis", contextId = "organClient", path = "/organ")
public interface OrganClient extends OrganApi {
}
