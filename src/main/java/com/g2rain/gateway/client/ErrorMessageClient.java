package com.g2rain.gateway.client;


import com.g2rain.infra.api.I18nMessageApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author alpha
 * @since 2026/4/16
 */
@FeignClient(name = "g2rain-infra", contextId = "errorMessageClient", path = "/i18n_message")
public interface ErrorMessageClient extends I18nMessageApi {
}
