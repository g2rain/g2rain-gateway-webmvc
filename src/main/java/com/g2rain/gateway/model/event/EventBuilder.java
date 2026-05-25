package com.g2rain.gateway.model.event;


import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.web.CachedBodyRequest;
import com.g2rain.gateway.utils.Constants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link GatewayEvent} 的构建器：按块写入身份、HTTP 头、请求/响应摘要，最后 {@link #build()} 得到不可再复用的实例。
 *
 * <p>本类为单次链式使用设计；{@link #build()} 返回的即为内部累积的同一 {@link GatewayEvent} 引用。</p>
 *
 * @author alpha
 * @since 2026/5/7
 */
@Slf4j
public final class EventBuilder {
    private static final JsonCodec jsonCodec = JsonCodecFactory.instance();

    private final GatewayEvent event = new GatewayEvent();

    EventBuilder() {
    }

    /**
     * @return 已填充的 {@link GatewayEvent}（与内部累积对象为同一引用）
     */
    public GatewayEvent build() {
        return event;
    }

    /**
     * 从当前请求的 {@link EdgePrincipalContext} 写入与网关事件对齐的身份字段。
     */
    public EventBuilder buildPrincipal(EdgePrincipalContext ctx) {
        if (Objects.isNull(ctx)) {
            return this;
        }

        event.setTraceId(ctx.getTraceId());
        event.setClientId(ctx.getClientId());
        event.setRequestId(ctx.getRequestId());
        event.setRequestTime(ctx.getRequestTime());
        event.setSessionType(ctx.getSessionType());
        event.setPassportId(ctx.getPassportId());
        event.setUserId(ctx.getUserId());
        event.setName(ctx.getName());
        event.setAdminUser(ctx.isAdminUser());
        event.setOrganId(ctx.getOrganId());
        event.setOrganName(ctx.getOrganName());
        event.setOrganType(ctx.getOrganType());
        event.setAdminCompany(ctx.isAdminCompany());
        event.setApplicationId(ctx.getApplicationId());
        event.setApplicationOrganId(ctx.getApplicationOrganId());
        event.setApplicationCode(ctx.getApplicationCode());
        return this;
    }

    /**
     * 写入审计关心的 HTTP 头（User-Agent、Host、转发 IP、Referer 等）。
     */
    public EventBuilder buildHeaders(HttpServletRequest request) {
        event.setAcceptLanguage(firstHeader(request, "Accept-Language"));
        event.setUserAgent(firstHeader(request, "User-Agent"));
        event.setHost(firstHeader(request, "Host", "X-Forwarded-Host"));
        event.setXForwardedFor(firstHeader(request, "X-Forwarded-For"));
        event.setXRealIp(firstHeader(request, "X-Real-IP", "X-Real-Ip"));
        event.setReferer(firstHeader(request, "Referer", "Referrer"));
        return this;
    }

    private String firstHeader(HttpServletRequest request, String... candidateNames) {
        for (String name : candidateNames) {
            String v = request.getHeader(name);
            if (Strings.isNotBlank(v)) {
                return v;
            }
        }

        return null;
    }

    /**
     * 将 query、path、method、请求体（若可自 {@link CachedBodyRequest} 读取）
     * 与响应摘要序列化为 JSON 字符串写入 {@link GatewayEvent#setPayload(String)}。
     */
    public EventBuilder buildPayload(HttpServletRequest request, byte[] rspBody) {
        event.setPath(request.getRequestURI());
        event.setMethod(request.getMethod());
        event.setPayload(doBuildPayload(request, rspBody));
        return this;
    }

    private String doBuildPayload(HttpServletRequest request, byte[] rspBody) {
        // 设置请求参数
        Map<String, Object> requestPart = new LinkedHashMap<>();
        requestPart.put("queryString", ServletUriComponentsBuilder.fromRequest(request).build().getQueryParams());
        requestPart.put("body", request.getAttribute(Constants.REQ_BODY_ATTRIBUTE));

        // 设置响应结果
        Map<String, Object> responsePart = jsonCodec.byte2obj(rspBody, new TypeReference<>() {
        });

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("request", requestPart);
        root.put("response", responsePart);
        return jsonCodec.obj2str(root);
    }
}
