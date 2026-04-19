package com.g2rain.gateway.filters;


import com.g2rain.common.exception.BaseError;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionProcessor;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.json.FailIgnoreFieldMixIn;
import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecBuilder;
import com.g2rain.common.model.Result;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 全局异常处理过滤器（Servlet/MVC）。
 *
 * <p>
 * 该过滤器包装整条 Servlet 过滤器链，统一兜底处理未被业务捕获的异常，并将异常转换为
 * {@link Result} JSON 响应体返回给客户端。
 * </p>
 *
 * <ul>
 *     <li>支持直接透传 {@link BusinessException} 的错误语义</li>
 *     <li>非业务异常统一包装为 {@link SystemErrorCode#SYSTEM_INTERNAL_ERROR}</li>
 *     <li>响应体由 {@link ExceptionProcessor} 负责国际化与标准化转换</li>
 *     <li>最终响应状态固定为 {@code 200}，错误信息放在统一响应体内</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/9/26
 */
@Slf4j
public class GlobalErrorFilter extends OncePerRequestFilter implements Ordered {
    /**
     * JSON 编码器，定制了对象的序列化规则
     */
    private final JsonCodec jsonSerializer = JsonCodecBuilder.builder()
        .withDefaults()
        .withConfig(jsonMapper -> {
            jsonMapper.addMixIn(Result.class, FailIgnoreFieldMixIn.class);
            jsonMapper.addMixIn(BaseError.class, FailIgnoreFieldMixIn.class);
        })
        .build();

    /**
     * 异常处理器，用于将异常转换为统一的结果
     */
    private final ExceptionProcessor exceptionProcessor;

    public GlobalErrorFilter(ExceptionProcessor exceptionProcessor) {
        this.exceptionProcessor = exceptionProcessor;
    }

    /**
     * 全局异常处理方法。
     *
     * @param request     原始 {@link HttpServletRequest} 对象
     * @param response    原始 {@link HttpServletResponse} 对象
     * @param filterChain 过滤器链, 用于继续执行后续过滤器和处理器
     * @throws ServletException 当过滤器链执行异常
     * @throws IOException      当读取或写入请求/响应体异常
     */
    @Override
    public void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            log.error("全局异常处理-{}: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);

            // 响应已提交，无法处理
            if (response.isCommitted()) {
                return;
            }

            // 获取上下文
            EdgePrincipalContext context = EdgePrincipalContextHolder.require();

            byte[] jsonBytes;
            if (ex instanceof BusinessException businessException) {
                jsonBytes = jsonSerializer.obj2byte(exceptionProcessor.process(
                    businessException, context.getAcceptLanguage()
                ));
            } else {
                BusinessException wrapped = new BusinessException(
                    SystemErrorCode.SYSTEM_INTERNAL_ERROR,
                    ex.getMessage()
                );
                jsonBytes = jsonSerializer.obj2byte(exceptionProcessor.process(
                    wrapped, context.getAcceptLanguage()
                ));
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentLength(jsonBytes.length);
            response.getOutputStream().write(jsonBytes);
            response.getOutputStream().flush();
        }
    }

    /**
     * 返回该异常处理器的优先级，值越大优先级越低。
     * 当前设置为 {@link Ordered#HIGHEST_PRECEDENCE} + 100
     *
     * @return 优先级值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
