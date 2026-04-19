/**
 * <p>
 * 本包（{@code exception}）包含网关层异常处理相关的核心类与接口，
 * 主要职责包括：
 * </p>
 * <ul>
 *     <li>定义网关业务异常类型（{@link com.g2rain.gateway.exception.GatewayException}），支持错误码、参数占位符、字段错误及底层异常</li>
 *     <li>全局异常捕获与统一处理（{@link com.g2rain.gateway.filters.GlobalErrorFilter}），将异常转换为标准 {@link com.g2rain.common.model.Result} 响应</li>
 *     <li>错误消息注册与本地化支持（{@link com.g2rain.gateway.exception.ErrorMessageStorage}）</li>
 *     <li>提供网关异常处理相关 Spring Bean 配置（{@link com.g2rain.gateway.config.GatewayConfig}）</li>
 * </ul>
 *
 * <h2>主要类说明</h2>
 * <ul>
 *     <li>{@link com.g2rain.gateway.exception.GatewayException}：网关业务异常，封装业务错误码、消息模板、字段错误及异常原因</li>
 *     <li>{@link com.g2rain.gateway.filters.GlobalErrorFilter}：WebFlux 全局异常处理器，拦截未处理异常并返回标准化 JSON 响应</li>
 *     <li>{@link com.g2rain.gateway.exception.ErrorMessageStorage}：错误消息注册与缓存实现，支持本地化消息查找</li>
 *     <li>{@link com.g2rain.gateway.config.GatewayConfig}：注册异常处理相关 Bean，包括 {@link com.g2rain.common.exception.ExceptionProcessor} 和 {@link com.g2rain.common.exception.ErrorMessageRegistry}</li>
 * </ul>
 *
 * <h2>功能示例</h2>
 * <pre>{@code
 * // 注册默认异常处理器
 * @Bean
 * public ExceptionProcessor exceptionProcessor(ErrorMessageRegistry registry) {
 *     return new DefaultExceptionProcessor(registry);
 * }
 *
 * // 使用 GlobalErrorHandler 捕获全局异常
 * @Bean
 * public GlobalErrorHandler globalErrorHandler(ExceptionProcessor processor) {
 *     return new GlobalErrorHandler(processor);
 * }
 *
 * // 抛出网关业务异常
 * if (someCondition) {
 *     throw new GatewayException(GatewayErrorCode.PARAM_NOT_BLANK, Map.of("value", "algorithm"));
 * }
 * }</pre>
 *
 * <h2>使用场景</h2>
 * <ul>
 *     <li>统一处理网关业务异常，返回标准化 JSON 响应</li>
 *     <li>支持错误消息本地化，避免客户端显示硬编码错误信息</li>
 *     <li>全局日志记录异常，便于审计与排查问题</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/15
 */
package com.g2rain.gateway.exception;
