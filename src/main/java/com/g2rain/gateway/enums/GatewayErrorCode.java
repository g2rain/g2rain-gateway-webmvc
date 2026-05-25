package com.g2rain.gateway.enums;

import com.g2rain.common.exception.ErrorCode;

/**
 * 网关错误码。
 *
 * @author jagger
 * @since 2026/3/24-19:28
 */
public enum GatewayErrorCode implements ErrorCode {
    TOKEN_INVALID("gateway.40001", "令牌无效"),
    TOKEN_EXPIRED("gateway.40002", "令牌已过期"),
    SUBSCRIPTION_EXPIRED("gateway.40003", "订阅已过期，请续费"),
    REQUEST_BODY_TOO_LARGE("gateway.40004", "请求体超过限制：{0:maxBytes} 字节"),
    API_KEY_INVALID("gateway.40005", "API Key 无效"),
    API_KEY_REVOKED("gateway.40006", "API Key 已吊销");

    private final String code;

    private final String messageTemplate;

    GatewayErrorCode(String code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String messageTemplate() {
        return messageTemplate;
    }
}
