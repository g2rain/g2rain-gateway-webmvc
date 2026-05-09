package com.g2rain.gateway.matcher;


import com.g2rain.common.utils.Strings;

import java.util.Objects;

/**
 * matcher 通用工具类
 *
 * <p>该类负责统一编译期与运行期的路径处理语义，确保路径标准化、
 * 动态路径分桶等逻辑在不同调用点保持一致</p>
 *
 * @author alpha
 * @since 2026/4/17
 */
public class MatcherUtils {
    /**
     * 根路径常量。
     */
    private static final String ROOT = "/";

    /**
     * 工具类不允许实例化
     */
    private MatcherUtils() {

    }

    /**
     * 计算路径的分桶 key
     *
     * <p>分桶规则优先保留前面连续的静态段，以减少动态规则候选集规模</p>
     *
     * @param path 已标准化或待标准化的路径
     * @return bucket key；无法稳定分桶时返回根路径 {@code /}
     */
    public static String getBucketKey(String path) {
        if (Objects.isNull(path) || path.length() < 2) {
            return ROOT;
        }

        int dynamicIdx = -1;
        for (int i = 0, j = path.length(); i < j; i++) {
            char c = path.charAt(i);
            if (c == '{' || c == '*') {
                dynamicIdx = i;
                break;
            }
        }

        int firstSlash = path.indexOf('/', 1);
        if (dynamicIdx != -1 && (firstSlash == -1 || dynamicIdx < firstSlash)) {
            return ROOT;
        }

        if (firstSlash == -1) {
            return path;
        }

        int secondSlash = path.indexOf('/', firstSlash + 1);
        if (dynamicIdx != -1 && (secondSlash == -1 || dynamicIdx < secondSlash)) {
            return path.substring(0, firstSlash);
        }

        return (secondSlash == -1) ? path : path.substring(0, secondSlash);
    }

    /**
     * 真实请求路径的分桶 key：按 {@code /} 切分后的<strong>前两个路径段</strong>拼接（不含模板符 {@code {}}、{@code *} 等）。
     *
     * <p>与 {@link #getBucketKey(String)} 分离：后者用于编译期 Springdoc/模式路径；本方法仅面向已标准化的<strong>实际请求 URI</strong>。</p>
     * <p>例：{@code /basis/1/user} → {@code /basis/1}；{@code /basis/user} → {@code /basis/user}；{@code /api} → {@code /api}；{@code /} → {@code /}。</p>
     *
     * @param normalizedPath 已 {@link #normalize(String)} 的请求路径
     * @return 分桶前缀；仅有根路径时返回 {@link #ROOT}
     */
    public static String requestBucketKey(String normalizedPath) {
        if (Objects.isNull(normalizedPath) || normalizedPath.length() <= 1) {
            return ROOT;
        }

        int firstSlash = normalizedPath.indexOf('/', 1);
        if (firstSlash == -1) {
            return normalizedPath;
        }

        int secondSlash = normalizedPath.indexOf('/', firstSlash + 1);
        return secondSlash == -1 ? normalizedPath : normalizedPath.substring(0, secondSlash);
    }

    /**
     * 标准化路径
     *
     * <p>标准化规则包括：</p>
     * <ul>
     *     <li>空白路径回退为根路径</li>
     *     <li>补齐前导斜杠</li>
     *     <li>折叠重复斜杠</li>
     *     <li>移除非根路径的尾部斜杠</li>
     * </ul>
     *
     * @param path 原始路径
     * @return 标准化后的路径
     */
    public static String normalize(String path) {
        if (Strings.isBlank(path)) {
            return ROOT;
        }

        if (!needsNormalize(path)) {
            return path;
        }

        return performNormalize(path);
    }

    /**
     * 判断路径是否需要标准化
     *
     * @param path 原始路径
     * @return {@code true} 表示需要执行标准化
     */
    private static boolean needsNormalize(String path) {
        if (path.charAt(0) != '/' || (path.length() > 1 && path.endsWith("/"))) {
            return true;
        }

        for (int i = 0, j = path.length(); i < j - 1; i++) {
            if (path.charAt(i) == '/' && path.charAt(i + 1) == '/') {
                return true;
            }
        }

        return false;
    }

    /**
     * 执行路径标准化
     *
     * @param path 原始路径
     * @return 标准化后的路径
     */
    private static String performNormalize(String path) {
        int len = path.length();
        StringBuilder sb = new StringBuilder(len);
        if (path.charAt(0) != '/') {
            sb.append('/');
        }

        boolean lastIsSlash = false;
        for (int i = 0; i < len; i++) {
            char c = path.charAt(i);
            if (c == '/') {
                if (lastIsSlash) {
                    continue;
                }

                lastIsSlash = true;
            } else {
                lastIsSlash = false;
            }

            sb.append(c);
        }

        int currentLen = sb.length();
        if (currentLen > 1 && sb.charAt(currentLen - 1) == '/') {
            sb.setLength(currentLen - 1);
        }

        return sb.toString();
    }
}
