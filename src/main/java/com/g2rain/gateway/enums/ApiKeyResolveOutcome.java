package com.g2rain.gateway.enums;

/**
 * {@link com.g2rain.gateway.cache.ApiKeyCache#resolve(String)} 对单次解析的归类结果。
 *
 * @author alpha
 * @since 2026/5/22
 */
public enum ApiKeyResolveOutcome {

    /** 令牌不存在、未激活、上下文缺失或回源失败。 */
    INVALID,

    /** 令牌存在且状态为已吊销。 */
    REVOKED,

    /** 令牌激活且会话上下文完整。 */
    ACTIVE
}
