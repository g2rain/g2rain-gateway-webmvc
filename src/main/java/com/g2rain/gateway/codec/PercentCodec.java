package com.g2rain.gateway.codec;


import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.BitSet;
import java.util.Objects;
import java.util.Set;

/**
 * <p>{@code PercentCodec} 提供对字符串进行百分号编码(Percent-Encoding)的功能</p>
 * <p>
 * 支持自定义安全字符集,并可选择将空格编码为加号(+)
 * 主要用于 URL 编码、路径编码等场景
 * </p>
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * PercentCodec codec = PercentCodec.of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~");
 * String encoded = codec.encode("测试路径 / example", StandardCharsets.UTF_8, Set.of());
 * System.out.println(encoded);
 * }</pre>
 *
 * @author alpha
 * @since 2025/10/6
 */
public class PercentCodec {

    /**
     * 百分号编码的十六进制字符表(大写)
     */
    private static final char[] HEX_ARRAY_UPPER = "0123456789ABCDEF".toCharArray();

    /**
     * 安全字符集合,表示无需编码的字符
     */
    private final BitSet safeCharacters;

    /**
     * 是否将空格编码为加号(+)
     */
    private boolean encodeSpaceAsPlus = false;

    /**
     * 创建 {@code PercentCodec} 的副本
     *
     * @param codec 原 {@code PercentCodec} 实例
     * @return 新的 {@code PercentCodec} 实例,包含相同的安全字符集
     */
    public static PercentCodec of(PercentCodec codec) {
        return new PercentCodec((BitSet) codec.safeCharacters.clone());
    }

    /**
     * 根据指定字符集合创建 {@code PercentCodec} 实例
     *
     * @param chars 安全字符集合
     * @return 新的 {@code PercentCodec} 实例
     */
    public static PercentCodec of(@NonNull CharSequence chars) {
        final PercentCodec codec = new PercentCodec();
        for (int i = 0, length = chars.length(); i < length; i++) {
            codec.addSafe(chars.charAt(i));
        }
        return codec;
    }

    /**
     * 构造一个默认的 {@code PercentCodec} 实例,安全字符集为空
     */
    public PercentCodec() {
        this(new BitSet(256));
    }

    /**
     * 使用指定的安全字符集构造 {@code PercentCodec} 实例
     *
     * @param safeCharacters 安全字符集
     */
    public PercentCodec(BitSet safeCharacters) {
        this.safeCharacters = safeCharacters;
    }

    /**
     * 添加安全字符,不进行百分号编码
     *
     * @param c 安全字符
     * @return 当前 {@code PercentCodec} 实例
     */
    @SuppressWarnings("UnusedReturnValue")
    public PercentCodec addSafe(char c) {
        safeCharacters.set(c);
        return this;
    }

    /**
     * 移除安全字符,使其进行百分号编码
     *
     * @param c 待移除的字符
     * @return 当前 {@code PercentCodec} 实例
     */
    public PercentCodec removeSafe(char c) {
        safeCharacters.clear(c);
        return this;
    }

    /**
     * 合并另一个 {@code PercentCodec} 的安全字符集到当前实例
     *
     * @param codec 待合并的 {@code PercentCodec}
     * @return 当前 {@code PercentCodec} 实例
     */
    public PercentCodec or(PercentCodec codec) {
        this.safeCharacters.or(codec.safeCharacters);
        return this;
    }

    /**
     * 创建新的 {@code PercentCodec} 实例,并合并指定 {@code PercentCodec} 的安全字符集
     *
     * @param codec 待合并的 {@code PercentCodec}
     * @return 新的 {@code PercentCodec} 实例
     */
    public PercentCodec orNew(PercentCodec codec) {
        return of(this).or(codec);
    }

    /**
     * 设置是否将空格编码为加号(+)
     *
     * @param encodeSpaceAsPlus {@code true} 编码为空格为加号,否则为空格进行百分号编码
     * @return 当前 {@code PercentCodec} 实例
     */
    @SuppressWarnings("UnusedReturnValue")
    public PercentCodec setEncodeSpaceAsPlus(boolean encodeSpaceAsPlus) {
        this.encodeSpaceAsPlus = encodeSpaceAsPlus;
        return this;
    }

    /**
     * 对指定字符序列进行百分号编码
     *
     * @param path           待编码的字符串
     * @param charset        字符集
     * @param customSafeChar 自定义安全字符集合(Unicode codepoint)
     * @return 百分号编码后的字符串,输入为空时返回 {@code null} 或原字符串
     */
    public String encode(CharSequence path, Charset charset, Set<Integer> customSafeChar) {
        if (Objects.isNull(charset) || Strings.isEmpty(path)) {
            return Objects.toString(path, null);
        }

        StringBuilder rewrittenPath = new StringBuilder(path.length());
        StringBuilder toEncode = new StringBuilder();

        int i = 0;
        int length = path.length();
        while (i < length) {
            // 按 codepoint 取字符
            int cp = Character.codePointAt(path, i);
            i += Character.charCount(cp);

            if (isSafe(cp, customSafeChar) || (encodeSpaceAsPlus && cp == ' ')) {
                if (!toEncode.isEmpty()) {
                    encodeToPercentEncoding(rewrittenPath, toEncode, charset);
                    toEncode.setLength(0);
                }

                if (cp != ' ') {
                    doAppendCodePoint(rewrittenPath, cp);
                } else {
                    rewrittenPath.append('+');
                }

                continue;
            }

            toEncode.appendCodePoint(cp);
        }

        if (!toEncode.isEmpty()) {
            encodeToPercentEncoding(rewrittenPath, toEncode, charset);
        }

        return rewrittenPath.toString();
    }

    /**
     * 检查字符是否安全(无需编码)
     *
     * @param cp     Unicode codepoint
     * @param custom 自定义安全字符集合
     * @return {@code true} 表示安全,否则需要编码
     */
    private boolean isSafe(int cp, Set<Integer> custom) {
        return safeCharacters.get(cp) || Collections.contains(custom, cp);
    }

    /**
     * 将 codepoint 添加到字符串构建器
     *
     * @param sb 字符串构建器
     * @param cp Unicode codepoint
     */
    private static void doAppendCodePoint(StringBuilder sb, int cp) {
        // BMP 范围直接追加
        if (Character.isBmpCodePoint(cp)) {
            sb.append((char) cp);
            return;
        }

        // 辅助平面,手动计算代理对
        sb.append(Character.highSurrogate(cp)).append(Character.lowSurrogate(cp));
    }

    /**
     * 对字符串进行百分号编码,并追加到结果字符串中
     *
     * @param rewrittenPath 结果字符串构建器
     * @param toEncode      待编码字符串
     * @param charset       字符集
     */
    private void encodeToPercentEncoding(StringBuilder rewrittenPath, CharSequence toEncode, Charset charset) {
        ByteBuffer byteBuffer = charset.encode(CharBuffer.wrap(toEncode));
        int val;
        while (byteBuffer.hasRemaining()) {
            // 百分号前缀
            rewrittenPath.append('%');
            val = byteBuffer.get() & 0xFF;
            // 高4位 + 低4位
            rewrittenPath.append(HEX_ARRAY_UPPER[val >>> 4]).append(HEX_ARRAY_UPPER[val & 0x0F]);
        }
    }
}
