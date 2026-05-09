package com.g2rain.gateway.enums;

import com.g2rain.common.exception.ErrorCode;

/**
 * 网关错误码
 *
 * @author jagger
 * @since 2026/3/24-19:28
 */
public enum GatewayErrorCode implements ErrorCode {
    TOKEN_INVALID("gateway.40001", "token invalid"),
    TOKEN_EXPIRED("gateway.40002", "token expired"),
    SUBSCRIPTION_EXPIRED("gateway.40003", "Subscription expired, please renew"),
    REQUEST_BODY_TOO_LARGE("gateway.40004", "Request body exceeds limit: {0:maxBytes} bytes");

    private final String code;

    private final String messageTemplate;

    /**
     * 构造系统错误码
     *
     * @param code            错误码（遵循4xxx客户端错误，5xxx服务器错误）
     * @param messageTemplate 消息模板（支持{0:param}顺序占位符或{key}键值对占位符）
     */
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
