package com.g2rain.gateway.client;


import com.g2rain.basis.api.LoginTokenApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 登录令牌 Feign 客户端（basis {@code /login_token}）。
 *
 * <p>
 * 静态 API Key 鉴权使用 {@link com.g2rain.basis.api.LoginTokenApi#fetchStaticTokenContext(String)}，
 * 由 {@link com.g2rain.gateway.cache.ApiKeyCache} 在缓存 miss 时调用。
 * </p>
 */
@FeignClient(name = "g2rain-basis", contextId = "loginTokenClient", path = "/login_token")
public interface LoginTokenClient extends LoginTokenApi {
}
