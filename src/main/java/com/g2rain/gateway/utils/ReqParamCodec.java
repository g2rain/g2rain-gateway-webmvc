package com.g2rain.gateway.utils;


import com.g2rain.common.utils.Collections;
import com.g2rain.gateway.codec.RFC3986Encoder;
import com.g2rain.gateway.model.web.CachedBodyRequest;
import jakarta.servlet.http.Part;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>{@code ReqParamCodec} 提供用于处理 HTTP 请求参数的工具方法，支持
 * {@code application/x-www-form-urlencoded} 和 {@code multipart/form-data} 编码格式。</p>
 * <p>
 * 主要用于将请求体解析为 {@link MultiValueMap}，方便后续参数读取和处理。
 * </p>
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * MultiValueMap<String, String> formParams = ReqParamCodec.processFormUrlEncodedBody(request);
 * Mono<MultiValueMap<String, String>> multipartParams =
 *     ReqParamCodec.processMultipartBody(data, filePart -> filePart.transferTo(new File("/tmp/" + filePart.filename()))
 *         .thenReturn(filePart.filename()));
 * }</pre>
 *
 * @author alpha
 * @since 2025/10/6
 */
@Slf4j
public class ReqParamCodec {

    /**
     * 私有构造，禁止实例化
     */
    private ReqParamCodec() {

    }

    /**
     * 对请求的 query 参数进行 RFC3986 编码、排序和拼接。
     *
     * @param params 请求的 query 参数
     * @return 规范化后的 query 字符串
     */
    public static String normalizeParams(MultiValueMap<@NonNull String, String> params) {
        if (Collections.isEmpty(params)) {
            return "";
        }

        return params.entrySet().stream()
            .flatMap(entry -> {
                String key = RFC3986Encoder.encode(entry.getKey());
                List<String> values = entry.getValue();
                if (Collections.isEmpty(values)) {
                    return Stream.of(key + "=");
                }

                return values.stream()
                    .map(RFC3986Encoder::encode)
                    .sorted()
                    .map(val -> key + "=" + val);
            })
            .sorted()
            .collect(Collectors.joining("&"));
    }

    /**
     * 解析 {@code application/x-www-form-urlencoded} 格式的请求体。
     *
     * @param request {@link CachedBodyRequest} 包含请求体的封装对象
     * @return {@link MultiValueMap}，包含解码后的表单参数键值对
     */
    public static MultiValueMap<@NonNull String, String> processFormUrlEncodedBody(CachedBodyRequest request) {
        String body = new String(request.asBytes(), StandardCharsets.UTF_8);
        MultiValueMap<@NonNull String, String> params = new LinkedMultiValueMap<>();

        int start = 0;
        int len = body.length();

        while (start < len) {
            int amp = body.indexOf('&', start);
            if (amp == -1) amp = len;

            int eq = body.indexOf('=', start);
            if (eq > amp || eq == -1) eq = amp;

            String key = UriUtils.decode(body.substring(start, eq), StandardCharsets.UTF_8);
            String val = eq < amp ? UriUtils.decode(body.substring(eq + 1, amp), StandardCharsets.UTF_8) : "";

            params.add(key, val);
            start = amp + 1;
        }

        return params;
    }

    /**
     * 解析 {@code multipart/form-data} 格式的请求体。
     *
     * @param request {@link CachedBodyRequest} 包含请求体的封装对象
     * @return {@link LinkedHashMap}，包含解码后的表单参数键值对
     */
    public static Map<String, List<String>> processMultipartBody(CachedBodyRequest request) {
        Map<String, List<String>> formParams = new LinkedHashMap<>();
        try {
            // 1. 将包装后的请求再次包装为 Spring 的 Multipart 请求
            StandardMultipartHttpServletRequest multipartRequest = new StandardMultipartHttpServletRequest(request);
            for (Part part : multipartRequest.getParts()) {
                // 文件字段，只记录文件名
                if (Objects.nonNull(part.getSubmittedFileName())) {
                    formParams.computeIfAbsent(part.getName(), k -> new ArrayList<>()).add(part.getSubmittedFileName());
                    continue;
                }

                // 普通表单字段
                String value = new String(part.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                formParams.computeIfAbsent(part.getName(), k -> new ArrayList<>()).add(value);
            }
        } catch (Exception e) {
            log.error("解析异常:{}", e.getMessage());
        }

        return formParams;
    }
}
