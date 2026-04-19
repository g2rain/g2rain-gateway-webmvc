package com.g2rain.gateway.matcher;


import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 规则编译器：将输入规则定义编译为运行期只读索引表
 * <p>
 * 编译阶段会完成以下工作：
 * <ul>
 *     <li>路径标准化与 PathPattern 预解析</li>
 *     <li>HTTP 方法集合转 methodMask（位运算友好）</li>
 *     <li>按 exact / buckets / global 三层索引分桶</li>
 *     <li>按优先级与模式特异性做稳定排序</li>
 * </ul>
 * </p>
 *
 * <p>
 * 容错原则：
 * <ul>
 *     <li>脏规则（空 path、空 target、非法 path）会被跳过</li>
 *     <li>methods 中非法 token 会被忽略，不中断整次编译</li>
 * </ul>
 * </p>
 *
 * @author alpha
 * @since 2026/4/16
 */
public class RuleCompiler<T> {

    /**
     * 全局兜底路径表达式
     */
    private static final String GLOBAL_PATTERN = "/**";
    /**
     * Spring 路径模式解析器（线程安全，可复用）
     */
    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    /**
     * 将原始规则列表编译为运行时规则表
     * <p>
     * 编译结果是不可变快照，适合被 {@link MatchEngine} 原子替换并并发读取
     * </p>
     *
     * @param rules 原始规则定义集合
     * @return 编译完成的不可变规则表；当入参为空时返回空表
     */
    public RuleTable<T> compile(Collection<RuleDefinition<T>> rules) {
        // 无规则直接返回共享空表，减少对象创建
        if (Collections.isEmpty(rules)) {
            return RuleTable.empty();
        }

        // 三层中间索引：先用 List 收集，最后统一转数组
        Map<String, List<MethodRule<T>>> exact = new HashMap<>();
        Map<String, List<MethodRule<T>>> buckets = new HashMap<>();
        List<MethodRule<T>> global = new ArrayList<>();

        for (RuleDefinition<T> r : rules) {
            // 跳过空规则、空 path、空 target，避免脏数据污染索引
            if (Objects.isNull(r) || Strings.isBlank(r.path()) || Objects.isNull(r.target())) {
                continue;
            }

            // 统一路径语义，确保编译期与运行期看到的是同一种 path
            String normalizedPath = MatcherUtils.normalize(r.path());
            PathPattern pattern;
            try {
                // 预解析 PathPattern，把解析成本前置到编译期
                pattern = PARSER.parse(normalizedPath);
            } catch (Exception ignored) {
                // 单条非法 path 不应影响整批规则编译
                continue;
            }

            // 把“字符串方法集合”编译成位掩码，运行期只做位运算
            MethodRule<T> mr = new MethodRule<>(
                parseMethodMask(r.methods()),
                pattern,
                r.target(),
                r.priority()
            );

            String patternStr = pattern.getPatternString();

            // 纯静态路径放入 exact，运行期可直接 O(1) 命中数组
            if (!patternStr.contains("*") && !patternStr.contains("{")) {
                exact.computeIfAbsent(patternStr, _ -> new ArrayList<>()).add(mr);
                continue;
            }

            // /** 作为全局兜底，放入 global 链
            if (GLOBAL_PATTERN.equals(patternStr)) {
                global.add(mr);
                continue;
            }

            // 其余动态路径按首段分桶，减少运行期候选规则数量
            buckets.computeIfAbsent(MatcherUtils.firstSegment(patternStr), _ -> new ArrayList<>()).add(mr);
        }

        // 桶内提前排好顺序，运行期命中首个即返回
        exact.replaceAll((_, v) -> sortRules(v));
        buckets.replaceAll((_, v) -> sortRules(v));
        sortRules(global);

        // copyOf 产出不可变 map，防止运行期被误改
        return new RuleTable<>(
            Map.copyOf(toArray(exact)),
            Map.copyOf(toArray(buckets)),
            toRuleArray(global)
        );
    }

    /**
     * 将中间态 List 索引转换为数组索引
     *
     * @param raw 中间态索引
     * @return value 为数组的最终索引
     */
    private Map<String, MethodRule<T>[]> toArray(Map<String, List<MethodRule<T>>> raw) {
        Map<String, MethodRule<T>[]> map = new HashMap<>();
        raw.forEach((k, v) -> map.put(k, toRuleArray(v)));
        return map;
    }

    /**
     * 解析 methods 字符串为方法位掩码
     * <p>
     * 解析失败 token 会被忽略；若全部 token 非法，则回退为 ALL。
     * </p>
     *
     * @param methods 规则中的方法配置（如 GET,POST / ALL / *）
     * @return 方法位掩码
     */
    private int parseMethodMask(String methods) {
        // 空值/ALL/* 统一视为“匹配所有方法”
        if (Strings.isBlank(methods) || "ALL".equalsIgnoreCase(methods) || "*".equals(methods)) {
            return RuleTable.ALL_METHOD_MASK;
        }

        int mask = 0;
        boolean hasValidMethod = false;
        for (String token : methods.split(",")) {
            // 支持 "GET, POST" 这类带空格配置，逐项 trim
            String method = token.trim();
            // 容忍连续逗号或空 token
            if (method.isEmpty()) {
                continue;
            }

            try {
                // 统一大写后走 HttpMethod 枚举，避免大小写差异导致失配
                method = method.toUpperCase(Locale.ROOT);
                // 逐个方法累加到位掩码，例如 GET|POST
                mask |= RuleTable.getMask(HttpMethod.valueOf(method));
                hasValidMethod = true;
            } catch (IllegalArgumentException ignored) {
                // 忽略非法方法名，避免配置脏数据中断全量编译
            }
        }

        // 全部 token 都非法时回退为 ALL，避免产出“永不命中”的死规则
        return hasValidMethod ? mask : RuleTable.ALL_METHOD_MASK;
    }

    /**
     * 对同一桶内规则做稳定排序
     * <p>
     * 先按 priority 降序，再按路径特异性排序，确保命中行为可预期
     * </p>
     *
     * @param list 桶内规则列表
     * @return 排序后的原列表
     */
    private List<MethodRule<T>> sortRules(List<MethodRule<T>> list) {
        // 0/1 条无需排序，直接返回
        if (list.size() <= 1) {
            return list;
        }

        // 先比业务优先级（高优先级在前），再比路径特异性（更具体在前）
        list.sort(Comparator.<MethodRule<T>, Integer>comparing(MethodRule::priority).reversed()
            .thenComparing(MethodRule::pattern, PathPattern.SPECIFICITY_COMPARATOR));
        return list;
    }

    /**
     * 将规则列表转换为数组
     *
     * @param rules 规则列表
     * @return 规则数组
     */
    @SuppressWarnings("unchecked")
    private MethodRule<T>[] toRuleArray(List<MethodRule<T>> rules) {
        return rules.toArray(MethodRule[]::new);
    }
}
