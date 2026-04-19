package com.g2rain.gateway.matcher;


import com.g2rain.common.utils.Strings;

import java.util.Objects;

/**
 * matcher 包内部通用工具方法集合
 * <p>
 * 主要职责：
 * <ul>
 *     <li>统一路径标准化逻辑，保证编译期与运行期使用相同路径语义</li>
 *     <li>提供首段提取能力，支持 {@link RuleTable} 的 buckets 分桶索引</li>
 * </ul>
 * </p>
 *
 * <p>
 * 该类仅包含无状态静态方法，不持有上下文，可被编译阶段与匹配阶段复用。
 * </p>
 *
 * @author alpha
 * @since 2026/4/17
 */
public class MatcherUtils {
    private MatcherUtils() {

    }

    /**
     * 根路径常量，用于首段路径提取时的统一返回值
     */
    private static final String ROOT = "/";

    /**
     * 提取路径首段（用于 buckets 分桶匹配）
     * <p>
     * 例：
     * <ul>
     *     <li>/api/user/1 -> /api</li>
     *     <li>/health -> /health</li>
     *     <li>/ -> /</li>
     * </ul>
     * </p>
     *
     * @param path 规范化路径
     * @return 首段路径 key
     */
    public static String firstSegment(String path) {
        if (Objects.isNull(path) || path.length() < 2) {
            return ROOT;
        }

        int idx = path.indexOf('/', 1);
        return idx == -1 ? path : path.substring(0, idx);
    }

    /**
     * 路径标准化：
     * <ul>
     *     <li>补齐前导 '/'</li>
     *     <li>合并重复斜杠</li>
     *     <li>移除非根路径尾部 '/'</li>
     * </ul>
     *
     * @param path 原始路径
     * @return 规范化路径
     */
    public static String normalize(String path) {
        // 保持入参语义：空路径由上游决定是否视为非法
        if (Strings.isBlank(path)) {
            return path;
        }

        String normalized = (path.startsWith("/") ? path : "/" + path).replaceAll("/+", "/");
        // 非根路径统一去尾斜杠，避免 /api/user 与 /api/user/ 被当作两条规则
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}
