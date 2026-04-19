package com.g2rain.gateway.enums;


import lombok.Getter;

import java.util.Arrays;

/**
 * <p>{@code HashAlgorithm} 枚举定义了支持的哈希算法及其编码名称。</p>
 * <p>
 * 当前仅支持 {@code SHA-256} 算法，方便统一管理和使用哈希算法。
 * </p>
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * if (HashAlgorithm.isNotExist("SHA-256")) {
 *     System.out.println("算法不存在");
 * } else {
 *     System.out.println("算法存在: " + HashAlgorithm.SHA256.getCode());
 * }
 * }</pre>
 *
 * @author alpha
 * @since 2025/9/30
 */
@Getter
public enum HashAlgorithm {

    /**
     * SHA-256 哈希算法
     */
    SHA256("SHA-256");

    /**
     * 哈希算法编码名称
     */
    private final String code;

    /**
     * 构造哈希算法枚举。
     *
     * @param code 算法编码名称，例如 "SHA-256"
     */
    HashAlgorithm(String code) {
        this.code = code;
    }

    /**
     * 判断给定的算法编码是否不存在于枚举中。
     *
     * @param code 算法编码
     * @return {@code true} 表示该算法编码不存在，{@code false} 表示存在
     */
    public static boolean isNotExist(String code) {
        return Arrays.stream(values())
            .map(HashAlgorithm::getCode)
            .noneMatch(haCode -> haCode.equals(code));
    }
}
