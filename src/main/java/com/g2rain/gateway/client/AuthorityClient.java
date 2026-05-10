package com.g2rain.gateway.client;


import com.g2rain.basis.api.AuthorityApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author alpha
 * @since 2026/5/5
 */
@FeignClient(name = "g2rain-basis", contextId = "authorityClient", path = "/authority")
public interface AuthorityClient extends AuthorityApi {
}
