package com.g2rain.gateway.filters;


import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.MediaTypes;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.PrincipalHeaders;
import com.g2rain.gateway.components.KafkaLogSender;
import com.g2rain.gateway.model.logger.JsonLog;
import com.g2rain.gateway.model.web.CachedBodyRequest;
import com.g2rain.gateway.model.web.CachedBodyResponse;
import com.g2rain.gateway.utils.ReqParamCodec;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 请求与响应日志记录过滤器。
 * <p>
 * 本过滤器用于在 Spring Cloud Gateway 中记录请求和响应信息，
 * 主要功能包括：
 * </p>
 * <ul>
 *     <li>记录请求的路径、方法、查询参数、请求头等基础信息</li>
 *     <li>根据请求 Content-Type 处理并记录请求体内容</li>
 *     <li>记录响应体（仅 JSON 数据）</li>
 * </ul>
 *
 * <h2>支持的请求体类型</h2>
 * <ul>
 *     <li>{@code application/json}</li>
 *     <li>{@code application/x-www-form-urlencoded}</li>
 *     <li>{@code multipart/form-data}</li>
 *     <li>其他类型仅记录基础信息</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Bean
 * public TraceLoggingFilter traceLoggingFilter() {
 *     return new TraceLoggingFilter();
 * }
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *     <li>需要配合 {@link CachedBodyRequest} 和 {@link CachedBodyResponse} 使用，以保证请求/响应体可重复读取</li>
 *     <li>对于非 JSON 响应，仅记录基础信息</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/6
 */
@Slf4j
@Component
@AllArgsConstructor
public class TraceLoggingFilter implements HandlerFilterFunction<ServerResponse, ServerResponse>, ResponseBodyProcessor {

    /**
     * 网关侧 Kafka 日志发送
     */
    private final KafkaLogSender kafkaLogSender;

    /**
     * {@code whiteListResolver} 用于判断当前请求是否命中白名单规则，
     * 如果命中则可以跳过当前 Filter 的执行。
     * <p>
     * 白名单规则包括全局规则和针对特定 Filter 的规则，匹配顺序为：
     * Filter 白名单 → 全局白名单，
     * 匹配方式包括 contextPath、exactPath、patternPath。
     * </p>
     */
    private final WhiteListResolver whiteListResolver;

    /**
     * 请求日志记录入口。
     *
     * <p>
     * 仅处理请求侧日志：命中白名单则跳过；否则采集路径、方法、请求头、请求体等信息后继续放行。
     * 响应侧日志由 {@link #process(HttpServletRequest, CachedBodyResponse, byte[])} 在响应回写前执行。
     * </p>
     *
     * @param req  当前请求
     * @param next 下游处理器
     * @return 下游响应
     * @throws Exception 下游处理异常
     */
    @Override
    public ServerResponse filter(@NonNull ServerRequest req, @NonNull HandlerFunction<ServerResponse> next) throws Exception {
        // 如果命中白名单，则跳过当前 Filter 的处理，直接进入下一个 Filter
        String filterName = this.getClass().getSimpleName();
        if (whiteListResolver.shouldExclude(filterName, req)) {
            return next.handle(req);
        }

        logRequest(req.servletRequest());
        return next.handle(req);
    }

    @Override
    public byte[] process(HttpServletRequest request, CachedBodyResponse response, byte[] body) {
        if (whiteListResolver.shouldExclude(processorName(), request)) {
            return body;
        }

        logResponse(body);
        return body;
    }

    /**
     * 记录响应日志。
     *
     * @param cachedBody 缓存的响应体
     */
    private void logResponse(byte[] cachedBody) {
        if (Collections.isEmpty(cachedBody)) {
            log.info("打印非 JSON 数据日志2");
            return;
        }

        log.info(new String(cachedBody, StandardCharsets.UTF_8));
    }

    /**
     * 记录请求日志。
     *
     * @param request 当前请求上下文
     */
    private void logRequest(HttpServletRequest request) {
        Map<String, Object> logMap = new LinkedHashMap<>();

        try {
            collectBasicRequestInfo(request, logMap);
        } catch (Exception e) {
            log.warn("收集基础请求信息失败", e);
        }

        String contentType = request.getContentType();
        if (Objects.isNull(contentType) || !(request instanceof CachedBodyRequest cached)) {
            printRequest(logMap);
            return;
        }

        if (MediaTypes.isFormUrlEncoded(contentType)) {
            processFormUrlEncodedBody(cached, logMap);
            return;
        }

        if (MediaTypes.isJson(contentType)) {
            processJsonBody(cached, logMap);
            return;
        }
        if (MediaTypes.isMultipartFormData(contentType)) {
            processMultipartBody(cached, logMap);
            return;
        }

        printRequest(logMap);
    }

    /**
     * 收集基础请求信息。
     *
     * @param request 当前请求上下文
     * @param logMap  存储日志信息的 Map
     */
    private void collectBasicRequestInfo(HttpServletRequest request, Map<String, Object> logMap) {
        logMap.put("请求路径", request.getRequestURI());
        logMap.put("请求方法", request.getMethod());
        logMap.put("查询参数", ServletUriComponentsBuilder.fromRequest(request).build().getQueryParams());
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String value = request.getHeader(name);
            headers.put(name, safeDecode(name, value));
        }
        logMap.put("请求头", headers);
    }

    private String safeDecode(String key, String value) {
        if (Strings.isBlank(value)) {
            return null;
        }

        String keyLower = key.toLowerCase();
        String nameLower = PrincipalHeaders.NAME.getLower();
        String organNameLower = PrincipalHeaders.ORGAN_NAME.getLower();
        if (!nameLower.equals(keyLower) && !organNameLower.equals(keyLower)) {
            return value;
        }

        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * 处理 application/x-www-form-urlencoded 类型请求体。
     *
     * @param cachedRequest 缓存请求体
     * @param logMap        存储日志信息的 Map
     */
    private void processFormUrlEncodedBody(CachedBodyRequest cachedRequest, Map<String, Object> logMap) {
        logMap.put("表单参数", ReqParamCodec.processFormUrlEncodedBody(cachedRequest));
        printRequest(logMap);
    }

    /**
     * 处理 application/json 类型请求体。
     *
     * @param cachedRequest 缓存请求体
     * @param logMap        存储日志信息的 Map
     */
    private void processJsonBody(CachedBodyRequest cachedRequest, Map<String, Object> logMap) {
        logMap.put("请求主体", new String(cachedRequest.asBytes(), StandardCharsets.UTF_8));
        printRequest(logMap);
    }

    /**
     * 处理 multipart/form-data 类型请求体。
     *
     * @param cachedRequest 缓存请求体
     * @param logMap        存储日志信息的 Map
     */
    private void processMultipartBody(CachedBodyRequest cachedRequest, Map<String, Object> logMap) {
        logMap.put("表单参数", ReqParamCodec.processMultipartBody(cachedRequest));
        printRequest(logMap);
    }

    /**
     * 打印请求日志。
     *
     * @param logMap 存储日志信息的 Map
     */
    private void printRequest(Map<String, Object> logMap) {
        log.info("请求信息 - {}", JsonCodecFactory.instance().obj2str(logMap));
        kafkaLogSender.send("logs", new JsonLog());
    }

    /**
     * 获取过滤器执行顺序。
     *
     * @return 过滤器顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 300;
    }

    @Override
    public String processorName() {
        return this.getClass().getSimpleName();
    }
}
