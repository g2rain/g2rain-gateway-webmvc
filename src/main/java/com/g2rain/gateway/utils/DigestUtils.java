package com.g2rain.gateway.utils;


import com.g2rain.gateway.enums.HashAlgorithm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 网关侧摘要工具。
 *
 * <p>
 * 用于 {@link com.g2rain.gateway.cache.ApiKeyCache}：对原始 API Key 做 SHA-256 再 hex 编码，
 * 与 basis {@code personal_static_access_token.token_hash} 及 cache-sync 载荷一致。
 * </p>
 *
 * @author alpha
 * @since 2026/5/22
 */
public final class DigestUtils {

    private DigestUtils() {
    }

    /**
     * 对 UTF-8 字符串计算 SHA-256，返回小写十六进制字符串。
     *
     * @param input 明文输入，通常为原始 API Key
     * @return 64 字符 hex 串
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HashAlgorithm.SHA256.getCode());
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
