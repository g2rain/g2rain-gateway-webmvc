package com.g2rain.gateway.config;


import com.g2rain.common.exception.DefaultExceptionProcessor;
import com.g2rain.common.exception.ErrorMessageRegistry;
import com.g2rain.common.exception.ExceptionProcessor;
import com.g2rain.gateway.client.ErrorMessageClient;
import com.g2rain.gateway.exception.ErrorMessageStorage;
import com.g2rain.gateway.filters.GlobalErrorFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关配置类，用于在微服务模块中注册全局异常处理相关的 Spring Bean。
 * <p>
 * 该配置主要注册以下 Bean：
 * <ul>
 *     <li>{@link ErrorMessageRegistry}：提供错误码到本地化消息的映射</li>
 *     <li>{@link ExceptionProcessor}：将 {@link com.g2rain.common.exception.BusinessException} 转换为统一 {@link com.g2rain.common.model.Result} 响应</li>
 *     <li>{@link GlobalErrorFilter}：全局异常处理器，拦截 WebFlux 异常并返回 JSON 响应</li>
 * </ul>
 * </p>
 * <p>
 * 所有 Bean 的依赖关系在此配置类中统一管理，保证 Spring 生命周期和依赖注入顺畅。
 * </p>
 *
 * @author alpha
 * @since 2025/10/15
 */
@Slf4j
@Configuration
public class GatewayConfig {

    /**
     * 注册默认的 {@link ErrorMessageRegistry} Bean。
     * <p>
     * 使用微服务模块提供的 {@link ErrorMessageStorage} 实现，负责提供错误码与本地化消息映射。
     * </p>
     *
     * @param client 注入的 {@link ErrorMessageClient} Bean
     * @return 默认的 {@link ErrorMessageRegistry} 实例
     */
    @Bean
    public ErrorMessageRegistry errorMessageRegistry(ErrorMessageClient client) {
        return new ErrorMessageStorage(client);
    }

    /**
     * 注册默认的 {@link ExceptionProcessor} Bean。
     * <p>
     * 使用传入的 {@link ErrorMessageRegistry} 构造 {@link DefaultExceptionProcessor}，
     * 将 {@link com.g2rain.common.exception.BusinessException} 转换为统一 {@link com.g2rain.common.model.Result} 响应，
     * 并根据错误码和本地化信息解析错误消息。
     * </p>
     *
     * @param registry 注入的 {@link ErrorMessageRegistry} Bean
     * @return 默认的 {@link ExceptionProcessor} 实例
     */
    @Bean
    public ExceptionProcessor defaultExceptionProcessor(ErrorMessageRegistry registry) {
        return new DefaultExceptionProcessor(registry);
    }

    /**
     * 注册全局异常处理器 {@link GlobalErrorFilter} Bean。
     * <p>
     * 该 Bean 拦截所有 WebFlux 异常，通过注入的 {@link ExceptionProcessor} 将异常转换为标准 JSON 响应。
     * </p>
     *
     * @param processor 注入的 {@link ExceptionProcessor} Bean
     * @return 全局异常处理器 {@link GlobalErrorFilter} 实例
     */
    @Bean
    public GlobalErrorFilter globalErrorHandler(ExceptionProcessor processor) {
        return new GlobalErrorFilter(processor);
    }
}
