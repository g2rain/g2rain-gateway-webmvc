package com.g2rain.gateway.filters;

import com.g2rain.gateway.model.web.CachedBodyResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;

/**
 * 响应体后处理器。
 *
 * <p>
 * 由 {@link CachedBodyFilter} 在响应写回客户端前统一调度，
 * 用于对已缓存的 JSON 响应体进行业务处理（如响应调整、日志打印等）。
 * </p>
 *
 * @author alpha
 * @since 2026/4/18
 */
public interface ResponseBodyProcessor extends Ordered {

    /**
     * 当前处理器对应的白名单名称（通常使用类名）。
     */
    String processorName();

    /**
     * 处理响应体并返回处理后的字节数组。
     *
     * @param request  请求对象
     * @param response 响应包装对象
     * @param body     已缓存响应体
     * @return 处理后的响应体（可直接返回原 body）
     */
    byte[] process(HttpServletRequest request, CachedBodyResponse response, byte[] body);
}
