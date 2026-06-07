package com.g2rain.gateway.matcher;


import org.springframework.http.HttpMethod;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * 运行期规则表
 *
 * <p>规则表是 matcher 在请求线程上读取的只读快照，按 HTTP 方法拆分为：
 * {@code methodBuckets} 与 {@code anyMethod} 两部分</p>
 *
 * @param methodBuckets 按单个 HTTP 方法位拆分后的路径索引
 * @param anyMethod     适用于全部 HTTP 方法的路径索引
 * @param <T>           业务目标对象类型
 * @author alpha
 * @since 2026/4/16
 */
public record RuleTable<T>(Map<Integer, PathIndex<T>> methodBuckets, PathIndex<T> anyMethod) {
    /**
     * 全方法掩码
     *
     * <p>当前实现覆盖 8 个标准 HTTP 方法，使用 8 位 bitmask 表示。</p>
     */
    public static final int ALL_METHOD_MASK = 0xFF;

    /**
     * 共享空规则表实例
     */
    private static final RuleTable<?> EMPTY = new RuleTable<>(Map.of(), PathIndex.empty());

    /**
     * 将 HTTP 方法转换为位掩码
     *
     * @param method HTTP 方法
     * @return 对应位掩码；未知方法回退为 {@link #ALL_METHOD_MASK}
     */
    public static int getMask(HttpMethod method) {
        return switch (method.name()) {
            case "GET" -> 1;
            case "POST" -> 1 << 1;
            case "PUT" -> 1 << 2;
            case "DELETE" -> 1 << 3;
            case "PATCH" -> 1 << 4;
            case "HEAD" -> 1 << 5;
            case "OPTIONS" -> 1 << 6;
            case "TRACE" -> 1 << 7;
            default -> ALL_METHOD_MASK;
        };
    }

    /**
     * 返回共享空规则表
     *
     * @param <T> 业务目标对象类型
     * @return 空规则表
     */
    @SuppressWarnings("unchecked")
    public static <T> RuleTable<T> empty() {
        return (RuleTable<T>) EMPTY;
    }

    /**
     * 单个方法维度下的路径索引
     *
     * @param exact   精确路径索引，key 为完整标准路径
     * @param buckets 动态路径分桶索引，key 为 bucketKey
     * @param global  全局兜底规则数组
     * @param <T>     业务目标对象类型
     */
    public record PathIndex<T>(Map<String, MatchRule<T>[]> exact, Map<String, MatchRule<T>[]> buckets,
                               MatchRule<T>[] global) {
        /**
         * 共享空路径索引实例
         */
        @SuppressWarnings("unchecked")
        private static final PathIndex<?> EMPTY = new PathIndex<>(Map.of(), Map.of(), (MatchRule<Object>[]) new MatchRule[0]);

        /**
         * 返回共享空路径索引
         *
         * @param <T> 业务目标对象类型
         * @return 空路径索引
         */
        @SuppressWarnings("unchecked")
        public static <T> PathIndex<T> empty() {
            return (PathIndex<T>) EMPTY;
        }

        @Override
        @SuppressWarnings("all")
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof PathIndex<?> other) {
                return Arrays.equals(this.global, other.global) &&
                    isMapArrayEqual(this.exact, other.exact) &&
                    isMapArrayEqual(this.buckets, other.buckets);
            }

            return false;
        }

        @Override
        public int hashCode() {
            // 仅计算 key 提高效率
            int result = Objects.hash(exact.keySet(), buckets.keySet());
            result = 31 * result + Arrays.hashCode(global);
            return result;
        }

        private static boolean isMapArrayEqual(Map<String, ?> m1, Map<String, ?> m2) {
            if (m1 == m2) return true;
            if (m1.size() != m2.size()) return false;
            for (var entry : m1.entrySet()) {
                Object v1 = entry.getValue();
                Object v2 = m2.get(entry.getKey());
                if (!(v1 instanceof Object[] a1 && v2 instanceof Object[] a2 && Arrays.equals(a1, a2))) {
                    return false;
                }
            }
            return true;
        }
    }
}
