package com.g2rain.gateway.filters;


import com.g2rain.common.utils.Collections;
import com.g2rain.gateway.model.web.CachedBodyRequest;
import com.g2rain.gateway.model.web.CachedBodyResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 全局请求体缓存过滤器,
 * 用于在过滤器链中缓存请求体和响应体, 支持重复读取请求内容和修改响应,
 * 避免原生 {@link HttpServletRequest} 和 {@link HttpServletResponse} 只能读取或写入一次的问题,
 * 可在日志记录, 签名验证, 或响应增强等场景使用
 * <p>
 * 通过包装请求和响应对象, 将请求体和响应体缓存到内存, 并在过滤器链结束后刷新响应
 * </p>
 *
 * <p>
 * 该过滤器顺序优先, 可通过 {@link #getOrder()} 调整优先级
 * </p>
 *
 * @author alpha
 * @since 2025/10/6
 */
@Slf4j
@Component
public class CachedBodyFilter extends OncePerRequestFilter implements Ordered {

    /**
     * 最大请求体缓存大小, 单位字节,
     * 默认值由配置 ${gateway.limits.request-body-max-size} 指定, 默认 10MB
     */
    @Value("${gateway.limits.request-body-max-size:10MB}")
    private DataSize maxInMemorySize;

    @Autowired(required = false)
    private List<ResponseBodyProcessor> responseBodyProcessors = List.of();

    /**
     * 过滤器核心逻辑,
     * 包装请求和响应对象, 缓存请求体和响应体, 并执行后续过滤器链,
     * 最终刷新响应缓存
     *
     * @param request     原始 {@link HttpServletRequest} 对象
     * @param response    原始 {@link HttpServletResponse} 对象
     * @param filterChain 过滤器链, 用于继续执行后续过滤器和处理器
     * @throws ServletException 当过滤器链执行异常
     * @throws IOException      当读取或写入请求/响应体异常
     */
    @Override
    public void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        // 包装并缓存请求体, 支持重复读取
        CachedBodyRequest requestWrapper = new CachedBodyRequest(
            request, maxInMemorySize.toBytes()
        );

        // 包装并缓存响应体, 支持重复写入和刷新
        CachedBodyResponse responseWrapper = new CachedBodyResponse(response);

        try {
            // 执行后续过滤器和处理器
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            try {
                applyResponseProcessors(requestWrapper, responseWrapper);
            } catch (Exception e) {
                log.error("网关后置响应体处理器执行失败，放弃高级加工，准备原样输出原始内容", e);
            }

            // 确保缓存响应体写入底层输出流
            responseWrapper.flush();
        }
    }

    /**
     * 统一调度响应后处理器（仅处理已缓存到内存中的响应体）。
     */
    private void applyResponseProcessors(HttpServletRequest request, CachedBodyResponse responseWrapper) {
        if (Collections.isEmpty(responseBodyProcessors)) {
            return;
        }

        byte[] body = responseWrapper.getBody();
        if (Objects.isNull(body)) {
            return;
        }

        List<ResponseBodyProcessor> orderedProcessors = responseBodyProcessors.stream()
            .sorted(Comparator.comparingInt(Ordered::getOrder))
            .toList();

        for (ResponseBodyProcessor processor : orderedProcessors) {
            try {
                body = processor.process(request, responseWrapper, body);
            } catch (Exception e) {
                log.error("响应处理器执行失败, processor={}", processor.processorName(), e);
            }
        }

        responseWrapper.refresh(body);
    }

    /**
     * 返回过滤器执行顺序,
     * 数值越小优先级越高,
     * 当前设置为 {@link Ordered#HIGHEST_PRECEDENCE} + 200
     *
     * @return 过滤器顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }
}
