package com.g2rain.gateway.model.auth;


import com.g2rain.basis.vo.StaticAccessTokenContextVo;
import com.g2rain.gateway.enums.ApiKeyResolveOutcome;

/**
 * API Key 缓存层解析结果（激活态上下文为 {@link StaticAccessTokenContextVo}）。
 *
 * @param outcome 三态枚举
 * @param context 仅 {@link ApiKeyResolveOutcome#ACTIVE} 时非空
 * @author alpha
 * @since 2026/5/22
 */
public record ApiKeyResolveResult(ApiKeyResolveOutcome outcome, StaticAccessTokenContextVo context) {

    public static ApiKeyResolveResult invalid() {
        return new ApiKeyResolveResult(ApiKeyResolveOutcome.INVALID, null);
    }

    public static ApiKeyResolveResult revoked() {
        return new ApiKeyResolveResult(ApiKeyResolveOutcome.REVOKED, null);
    }

    /**
     * @param context basis 会话 VO，不可为 null
     */
    public static ApiKeyResolveResult active(StaticAccessTokenContextVo context) {
        return new ApiKeyResolveResult(ApiKeyResolveOutcome.ACTIVE, context);
    }
}
