package com.g2rain.gateway.config;

import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * 网关代理使用的 RestClient 配置，支持 lb:// 协议的服务发现与负载均衡。
 * <p>
 * Spring Cloud Gateway Server MVC 在转发请求时使用 RestClient 访问目标 URI。
 * 当路由目标为 {@code lb://service-id} 时，必须使用支持 LoadBalancer 的 RestClient，
 * 否则会抛出 {@code Unroutable protocol scheme: lb://service-id}。
 * </p>
 * <p>
 * 通过注册 {@link LoadBalanced} 的 {@link RestClient.Builder}，Spring Cloud LoadBalancer
 * 会在实际请求前将 lb://service-id 解析为具体的 http://host:port。
 * </p>
 *
 * @author alpha
 * @since 2026/3/15
 */
@Configuration
public class LoadBalancerConfig {

    /**
     * 注册支持负载均衡的 RestClient.Builder，供网关代理使用。
     * <p>
     * Spring Cloud LoadBalancer 会对带 {@link LoadBalanced} 的 Builder 注入解析 lb:// 的
     * ClientHttpRequestFactory 或拦截器，从而将 lb://service-id 解析为具体实例地址。
     * </p>
     *
     * @return 支持 lb:// 的 RestClient.Builder
     */
    @Bean
    @Primary
    @LoadBalanced
    public RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }
}
