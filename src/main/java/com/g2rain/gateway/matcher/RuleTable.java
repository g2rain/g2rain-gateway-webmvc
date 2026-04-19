package com.g2rain.gateway.matcher;


import org.springframework.http.HttpMethod;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * 不可变规则表（运行期核心索引结构）
 * <p>
 * 这个类是 matcher 基座在请求阶段的核心数据结构，目标是：
 * <ul>
 *     <li>高并发读：请求线程只读，无锁访问</li>
 *     <li>原子替换：规则刷新时整表替换，避免脏读中间态</li>
 *     <li>快速定位：通过 exact / buckets / global 三层索引降低匹配范围</li>
 * </ul>
 * </p>
 *
 * <p>
 * 三个索引分层语义：
 * <ul>
 *     <li>exact：纯静态路径，按完整路径字符串 O(1) 取数组</li>
 *     <li>buckets：带变量或通配符路径，按首段路径分桶</li>
 *     <li>global：兜底规则（典型如 /&#42;&#42;）</li>
 * </ul>
 * </p>
 *
 * <p>
 * 为什么 value 用数组而不是 List：
 * <ul>
 *     <li>数组在只读场景下对象层次更浅，遍历更轻</li>
 *     <li>编译期一次性构建，运行期只读，不需要动态增删</li>
 * </ul>
 * </p>
 *
 * @param exact   精确路径索引，key=规范化后的完整 path
 * @param buckets 分桶索引，key=path 首段（如 /api）
 * @param global  全局兜底规则数组
 * @author alpha
 * @since 2026/4/16
 */
public record RuleTable<T>(Map<String, MethodRule<T>[]> exact, Map<String, MethodRule<T>[]> buckets, MethodRule<T>[] global) {

    /**
     * ALL 方法掩码，8 位全 1（对应 GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS/TRACE）
     */
    public static final int ALL_METHOD_MASK = 0xFF;

    /**
     * HTTP 方法与 bit 位的映射表
     * <p>
     * 设计目标：将“方法集合匹配”从字符串比较降为位运算
     * </p>
     */
    private static final Map<HttpMethod, Integer> METHOD_MASKS = Map.of(
        // GET 方法对应 0b00000001
        HttpMethod.GET,     1,
        // POST 方法对应 0b00000010
        HttpMethod.POST,    1 << 1,
        // PUT 方法对应 0b00000100
        HttpMethod.PUT,     1 << 2,
        // DELETE 方法对应 0b00001000
        HttpMethod.DELETE,  1 << 3,
        // PATCH 方法对应 0b00010000
        HttpMethod.PATCH,   1 << 4,
        // HEAD 方法对应 0b00100000
        HttpMethod.HEAD,    1 << 5,
        // OPTIONS 方法对应 0b01000000
        HttpMethod.OPTIONS, 1 << 6,
        // TRACE 方法对应 0b10000000
        HttpMethod.TRACE,   1 << 7
    );

    /**
     * 共享空表实例
     * <p>
     * 采用单例可以减少“无规则场景”下的对象创建与 GC 压力
     * </p>
     */
    @SuppressWarnings("unchecked")
    private static final RuleTable<?> EMPTY = new RuleTable<>(
        Map.of(),
        Map.of(),
        (MethodRule<Object>[]) new MethodRule[0]
    );

    /**
     * 获取方法掩码
     *
     * @param method HTTP 方法；当为 null 时表示 ALL（所有方法）
     * @return 对应 bit mask
     */
    public static int getMask(HttpMethod method) {
        return Objects.isNull(method) ? ALL_METHOD_MASK : METHOD_MASKS.getOrDefault(method, 0);
    }

    /**
     * 返回全局共享空规则表
     * <p>
     * 该实例可作为“无规则”场景的统一快照基线，避免多处重复创建空对象
     * </p>
     */
    @SuppressWarnings("unchecked")
    public static <T> RuleTable<T> empty() {
        return (RuleTable<T>) EMPTY;
    }

    /**
     * 语义化相等比较
     * <p>
     * 这里不能直接依赖 record 自动生成的 equals，因为 Map 的 value 是数组，
     * 数组默认 equals 比较的是引用地址，不是元素内容。我们需要“值语义”：
     * <ul>
     *     <li>同 key 下数组元素逐项相等，视为相等</li>
     *     <li>不同引用但元素内容一致，也应判定为相等</li>
     * </ul>
     * </p>
     *
     * @param o 对比对象
     * @return 是否值语义相等
     */
    @Override
    public boolean equals(Object o) {
        // 快速路径：同一对象引用直接相等
        if (this == o) return true;

        // JDK25 record pattern：结构解构后进行字段级比较
        if (o instanceof RuleTable(var otherExact, var otherBuckets, var otherGlobal)) {
            return mapEquals(exact, otherExact)
                && mapEquals(buckets, otherBuckets)
                // global 是数组，必须使用 Arrays.equals 做元素比较
                && Arrays.equals(global, otherGlobal);
        }

        return false;
    }

    /**
     * Map 值语义比较（重点处理 value 为数组的情况）
     *
     * @param m1 左侧 map
     * @param m2 右侧 map
     * @return 两个 map 是否值语义相等
     */
    private static boolean mapEquals(Map<String, ?> m1, Map<String, ?> m2) {
        // 大小不同可直接判定不等
        if (m1.size() != m2.size()) return false;

        // 按 key 对齐比较 value
        for (var e : m1.entrySet()) {
            Object left = e.getValue();
            Object right = m2.get(e.getKey());
            // value 可能是数组，因此委托到数组感知比较函数
            if (!arrayValueEquals(left, right)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 与 equals 保持一致的哈希实现
     * <p>
     * 核心要求：如果两个 RuleTable 在 equals 下相等，则 hashCode 必须一致
     * 因此 map value 为数组时，必须使用数组内容哈希，而不是对象身份哈希
     * </p>
     *
     * @return 值语义 hash
     */
    @Override
    public int hashCode() {
        // 先计算两个 map 的内容哈希
        int h = mapHash(exact);
        h = 31 * h + mapHash(buckets);
        // global 是数组，必须按元素内容计算哈希
        h = 31 * h + Arrays.hashCode(global);
        return h;
    }

    /**
     * 计算 map 的值语义哈希
     *
     * @param map 待计算 map
     * @return map hash
     */
    private static int mapHash(Map<String, ?> map) {
        int h = 0;
        for (var e : map.entrySet()) {
            // value 可能为数组，需走数组感知哈希
            int valueHash = arrayValueHash(e.getValue());
            h += e.getKey().hashCode() ^ valueHash;
        }
        return h;
    }

    /**
     * 支持数组值语义的 equals
     *
     * @param left  左值
     * @param right 右值
     * @return 是否相等
     */
    private static boolean arrayValueEquals(Object left, Object right) {
        // 同引用（包括同为 null）直接相等
        if (left == right) return true;

        // 一方为空，另一方非空，直接不等
        if (Objects.isNull(left) || Objects.isNull(right)) return false;

        // 对象数组按元素比较，不按引用比较
        if (left instanceof Object[] leftArr && right instanceof Object[] rightArr) {
            return Arrays.equals(leftArr, rightArr);
        }

        // 非数组回退到普通对象 equals
        return Objects.equals(left, right);
    }

    /**
     * 支持数组值语义的 hash
     *
     * @param value 任意对象或数组
     * @return 哈希值
     */
    private static int arrayValueHash(Object value) {
        // 与 arrayValueEquals 对齐：null 的 hash 固定为 0
        if (Objects.isNull(value)) return 0;

        // 对象数组按元素内容计算 hash
        if (value instanceof Object[] arr) {
            return Arrays.hashCode(arr);
        }

        // 非数组回退到对象自身 hashCode
        return value.hashCode();
    }
}
