package com.g2rain.gateway.codec;


import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * <p>{@code RFC3986Encoder} 提供符合 <a href="https://datatracker.ietf.org/doc/html/rfc3986">RFC 3986</a> 标准的 URL 编码功能</p>
 * <p>
 * 默认使用 {@link RFC3986#QUERY} 规则对字符串进行编码,可指定字符集
 * 常用于对 URL 查询参数或路径进行安全编码
 * </p>
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * String encoded = RFC3986Encoder.encode("测试路径 / example");
 * String encodedUtf16 = RFC3986Encoder.encode("测试路径 / example", StandardCharsets.UTF_16);
 * System.out.println(encoded);
 * System.out.println(encodedUtf16);
 * }</pre>
 *
 * @author alpha
 * @since 2025/10/6
 */
public class RFC3986Encoder {

    /**
     * 私有构造,禁止实例化
     */
    private RFC3986Encoder() {

    }

    /**
     * 使用 UTF-8 字符集对 URL 进行 RFC3986 编码
     *
     * @param url 待编码的 URL 字符串
     * @return 编码后的 URL 字符串,输入为空时返回 {@code null}
     */
    public static String encode(String url) {
        return encode(url, StandardCharsets.UTF_8);
    }

    /**
     * 使用指定字符集对 URL 进行 RFC3986 编码
     *
     * @param url     待编码的 URL 字符串
     * @param charset 字符集
     * @return 编码后的 URL 字符串,输入为空时返回 {@code null}
     */
    public static String encode(String url, Charset charset) {
        return RFC3986.QUERY.encode(url, charset, null);
    }
}
