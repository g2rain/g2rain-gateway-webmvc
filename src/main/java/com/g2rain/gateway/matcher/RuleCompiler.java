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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 规则编译器
 *
 * <p>该类负责把外部规则定义转换为 matcher 可直接读取的运行期结构，
 * 同时提供单条规则的增量 upsert/remove 能力</p>
 *
 * @param <T> 业务目标对象类型
 * @author alpha
 * @since 2026/4/16
 */
public class RuleCompiler<T> {
    /**
     * Spring 路径模式解析器
     */
    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    /**
     * 规则排序器
     *
     * <p>优先按路径特异性排序，若特异性相同，则按规则 ID 升序兜底</p>
     */
    private static final Comparator<MatchRule<?>> RULE_COMPARATOR = (left, right) -> {
        int result = PathPattern.SPECIFICITY_COMPARATOR.compare(left.pattern(), right.pattern());
        if (result != 0) {
            return result;
        }

        return Comparator.nullsLast(Long::compareTo).compare(left.id(), right.id());
    };

    /**
     * 全量编译规则集合
     *
     * @param rules 原始规则集合
     * @return 编译后的运行期规则表
     */
    public RuleTable<T> compile(Collection<RuleDefinition<T>> rules) {
        if (Collections.isEmpty(rules)) {
            return RuleTable.empty();
        }

        var methodBuckets = new HashMap<Integer, MutableBucket<T>>();
        var anyMethodBucket = new MutableBucket<T>();
        for (RuleDefinition<T> definition : rules) {
            compileRule(definition).ifPresent(compiled -> addCompiledRule(
                methodBuckets, anyMethodBucket, compiled
            ));
        }

        var finalBuckets = new HashMap<Integer, RuleTable.PathIndex<T>>();
        methodBuckets.forEach((m, b) -> finalBuckets.put(m, toPathIndex(sortBucket(b))));
        return new RuleTable<>(Map.copyOf(finalBuckets), toPathIndex(sortBucket(anyMethodBucket)));
    }

    /**
     * 在现有规则表上插入或替换单条规则
     *
     * @param table      现有规则表
     * @param definition 新规则定义
     * @return 更新后的规则表
     */
    public RuleTable<T> upsert(RuleTable<T> table, RuleDefinition<T> definition) {
        return compileRule(definition)
            .map(compiled -> upsertCompiledRule(safeTable(table), compiled))
            .orElseGet(() -> safeTable(table));
    }

    /**
     * 在现有规则表上删除单条规则
     *
     * @param table      现有规则表
     * @param definition 待删除规则定义
     * @return 更新后的规则表
     */
    public RuleTable<T> remove(RuleTable<T> table, RuleDefinition<T> definition) {
        return compileRule(definition)
            .map(compiled -> removeCompiledRule(safeTable(table), compiled))
            .orElseGet(() -> safeTable(table));
    }

    /**
     * 描述规则会影响的版本 scope
     *
     * <p>该方法主要用于缓存局部失效计算</p>
     *
     * @param definition 规则定义
     * @return 规则对应的 scope 描述
     */
    public Optional<RuleScope> describe(RuleDefinition<T> definition) {
        return compileRule(definition).map(compiled -> new RuleScope(
            compiled.slotType(), compiled.key(), compiled.rule().methodMask(), compiled.methodBits()
        ));
    }

    /**
     * 对外部传入的规则表做空值保护
     *
     * @param table 规则表
     * @return 非空规则表
     */
    private RuleTable<T> safeTable(RuleTable<T> table) {
        return Objects.nonNull(table) ? table : RuleTable.empty();
    }

    /**
     * 将单条规则编译为内部结构
     *
     * @param definition 原始规则定义
     * @return 编译后的内部规则对象
     */
    private Optional<CompiledRule<T>> compileRule(RuleDefinition<T> definition) {
        if (Objects.isNull(definition) || Strings.isBlank(definition.path()) || Objects.isNull(definition.target())) {
            return Optional.empty();
        }

        String normalizedPath = MatcherUtils.normalize(definition.path());
        PathPattern pattern;
        try {
            pattern = PARSER.parse(normalizedPath);
        } catch (Exception ignored) {
            return Optional.empty();
        }

        int methodMask = parseMethodMask(definition.methods());
        MatchRule<T> matchRule = new MatchRule<>(definition.id(), methodMask, pattern, definition.target());
        if (!normalizedPath.contains("*") && !normalizedPath.contains("{")) {
            return Optional.of(new CompiledRule<>(matchRule, SlotType.EXACT, normalizedPath, methodBits(methodMask)));
        }

        String bucketKey = MatcherUtils.getBucketKey(normalizedPath);
        if ("/".equals(bucketKey)) {
            return Optional.of(new CompiledRule<>(matchRule, SlotType.GLOBAL, bucketKey, methodBits(methodMask)));
        }

        return Optional.of(new CompiledRule<>(matchRule, SlotType.BUCKET, bucketKey, methodBits(methodMask)));
    }

    /**
     * 将编译后的规则加入中间桶结构
     *
     * @param methodBuckets   按方法拆分的中间桶
     * @param anyMethodBucket 全方法中间桶
     * @param compiledRule    编译后的规则
     */
    private void addCompiledRule(Map<Integer, MutableBucket<T>> methodBuckets, MutableBucket<T> anyMethodBucket,
                                 CompiledRule<T> compiledRule) {
        if (compiledRule.isAnyMethod()) {
            addToBucket(anyMethodBucket, compiledRule);
            return;
        }

        for (int bit : compiledRule.methodBits()) {
            MutableBucket<T> bucket = methodBuckets.computeIfAbsent(bit, _ -> new MutableBucket<>());
            addToBucket(bucket, compiledRule);
        }
    }

    /**
     * 将规则加入指定桶
     *
     * @param bucket       目标桶
     * @param compiledRule 编译后的规则
     */
    private void addToBucket(MutableBucket<T> bucket, CompiledRule<T> compiledRule) {
        switch (compiledRule.slotType()) {
            case EXACT ->
                bucket.exact().computeIfAbsent(compiledRule.key(), _ -> new ArrayList<>()).add(compiledRule.rule());
            case BUCKET ->
                bucket.buckets().computeIfAbsent(compiledRule.key(), _ -> new ArrayList<>()).add(compiledRule.rule());
            case GLOBAL -> bucket.global().add(compiledRule.rule());
        }
    }

    /**
     * 对规则表执行单条 upsert
     *
     * @param table        现有规则表
     * @param compiledRule 编译后的规则
     * @return 更新后的规则表
     */
    private RuleTable<T> upsertCompiledRule(RuleTable<T> table, CompiledRule<T> compiledRule) {
        if (compiledRule.isAnyMethod()) {
            return new RuleTable<>(table.methodBuckets(), addToPathIndex(table.anyMethod(), compiledRule));
        }

        Map<Integer, RuleTable.PathIndex<T>> methodBuckets = new HashMap<>(table.methodBuckets());
        for (int bit : compiledRule.methodBits()) {
            RuleTable.PathIndex<T> updated = addToPathIndex(
                methodBuckets.getOrDefault(bit, RuleTable.PathIndex.empty()),
                compiledRule
            );
            methodBuckets.put(bit, updated);
        }

        return new RuleTable<>(Map.copyOf(methodBuckets), table.anyMethod());
    }

    /**
     * 对规则表执行单条 remove
     *
     * @param table        现有规则表
     * @param compiledRule 编译后的规则
     * @return 更新后的规则表
     */
    private RuleTable<T> removeCompiledRule(RuleTable<T> table, CompiledRule<T> compiledRule) {
        if (compiledRule.isAnyMethod()) {
            return new RuleTable<>(table.methodBuckets(), removeFromPathIndex(table.anyMethod(), compiledRule));
        }

        Map<Integer, RuleTable.PathIndex<T>> methodBuckets = new HashMap<>(table.methodBuckets());
        for (int bit : compiledRule.methodBits()) {
            RuleTable.PathIndex<T> current = methodBuckets.get(bit);
            if (Objects.isNull(current)) {
                continue;
            }

            RuleTable.PathIndex<T> updated = removeFromPathIndex(current, compiledRule);
            if (isEmpty(updated)) {
                methodBuckets.remove(bit);
            } else {
                methodBuckets.put(bit, updated);
            }
        }

        return new RuleTable<>(Map.copyOf(methodBuckets), table.anyMethod());
    }

    /**
     * 向路径索引插入单条规则
     *
     * @param index        原路径索引
     * @param compiledRule 编译后的规则
     * @return 更新后的路径索引
     */
    private RuleTable.PathIndex<T> addToPathIndex(RuleTable.PathIndex<T> index, CompiledRule<T> compiledRule) {
        return switch (compiledRule.slotType()) {
            case EXACT -> new RuleTable.PathIndex<>(
                updatePathMap(index.exact(), compiledRule.key(), compiledRule.rule(), true),
                index.buckets(),
                index.global()
            );
            case BUCKET -> new RuleTable.PathIndex<>(
                index.exact(),
                updatePathMap(index.buckets(), compiledRule.key(), compiledRule.rule(), true),
                index.global()
            );
            case GLOBAL -> new RuleTable.PathIndex<>(
                index.exact(),
                index.buckets(),
                updateRuleArray(index.global(), compiledRule.rule(), true)
            );
        };
    }

    /**
     * 从路径索引删除单条规则
     *
     * @param index        原路径索引
     * @param compiledRule 编译后的规则
     * @return 更新后的路径索引
     */
    private RuleTable.PathIndex<T> removeFromPathIndex(RuleTable.PathIndex<T> index, CompiledRule<T> compiledRule) {
        return switch (compiledRule.slotType()) {
            case EXACT -> new RuleTable.PathIndex<>(
                updatePathMap(index.exact(), compiledRule.key(), compiledRule.rule(), false),
                index.buckets(),
                index.global()
            );
            case BUCKET -> new RuleTable.PathIndex<>(
                index.exact(),
                updatePathMap(index.buckets(), compiledRule.key(), compiledRule.rule(), false),
                index.global()
            );
            case GLOBAL -> new RuleTable.PathIndex<>(
                index.exact(),
                index.buckets(),
                updateRuleArray(index.global(), compiledRule.rule(), false)
            );
        };
    }

    /**
     * 更新 exact 或 bucket 结构中的单个数组槽位
     *
     * @param raw  原始 map
     * @param key  路径 key
     * @param rule 目标规则
     * @param add  是否为新增操作
     * @return 更新后的 map
     */
    private Map<String, MatchRule<T>[]> updatePathMap(Map<String, MatchRule<T>[]> raw, String key, MatchRule<T> rule, boolean add) {
        MatchRule<T>[] current = raw.get(key);
        MatchRule<T>[] updated = updateRuleArray(current, rule, add);

        Map<String, MatchRule<T>[]> result = new HashMap<>(raw);
        if (updated.length == 0) {
            result.remove(key);
        } else {
            result.put(key, updated);
        }

        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    /**
     * 更新规则数组
     *
     * <p>该方法会先按规则 ID 去重，再在新增场景中重新排序</p>
     *
     * @param current 当前规则数组
     * @param rule    目标规则
     * @param add     是否为新增操作
     * @return 更新后的规则数组
     */
    @SuppressWarnings("unchecked")
    private MatchRule<T>[] updateRuleArray(MatchRule<T>[] current, MatchRule<T> rule, boolean add) {
        List<MatchRule<T>> rules = new ArrayList<>();
        if (Objects.nonNull(current)) {
            for (MatchRule<T> item : current) {
                if (!Objects.equals(item.id(), rule.id())) {
                    rules.add(item);
                }
            }
        }

        if (add) {
            rules.add(rule);
            rules.sort(RULE_COMPARATOR);
        }

        return rules.toArray(MatchRule[]::new);
    }

    /**
     * 判断路径索引是否为空
     *
     * @param index 路径索引
     * @return {@code true} 表示索引中没有任何规则
     */
    private boolean isEmpty(RuleTable.PathIndex<T> index) {
        return index.exact().isEmpty() && index.buckets().isEmpty() && index.global().length == 0;
    }

    /**
     * 将中间桶结构转为运行期路径索引
     *
     * @param bucket 中间桶结构
     * @return 运行期路径索引
     */
    @SuppressWarnings("unchecked")
    private RuleTable.PathIndex<T> toPathIndex(MutableBucket<T> bucket) {
        return new RuleTable.PathIndex<>(
            convertMap(bucket.exact()),
            convertMap(bucket.buckets()),
            bucket.global().toArray(MatchRule[]::new)
        );
    }

    /**
     * 将中间 map 转为不可变数组 map
     *
     * @param raw 中间 map
     * @return 不可变数组 map
     */
    @SuppressWarnings("unchecked")
    private Map<String, MatchRule<T>[]> convertMap(Map<String, List<MatchRule<T>>> raw) {
        if (raw.isEmpty()) {
            return Map.of();
        }

        var result = new HashMap<String, MatchRule<T>[]>(raw.size());
        raw.forEach((k, v) -> result.put(k, v.toArray(MatchRule[]::new)));
        return Map.copyOf(result);
    }

    /**
     * 对桶内规则执行排序
     *
     * @param bucket 中间桶结构
     * @return 排序后的桶
     */
    private MutableBucket<T> sortBucket(MutableBucket<T> bucket) {
        bucket.exact().values().forEach(l -> l.sort(RULE_COMPARATOR));
        bucket.buckets().values().forEach(l -> l.sort(RULE_COMPARATOR));
        bucket.global().sort(RULE_COMPARATOR);
        return bucket;
    }

    /**
     * 解析 HTTP 方法字符串为位掩码
     *
     * @param methods 方法字符串
     * @return 方法位掩码
     */
    private int parseMethodMask(String methods) {
        if (Strings.isBlank(methods) || "*".equals(methods) || "ALL".equalsIgnoreCase(methods)) {
            return RuleTable.ALL_METHOD_MASK;
        }

        int mask = 0;
        for (String part : methods.split(",")) {
            HttpMethod httpMethod = HttpMethod.valueOf(part.trim().toUpperCase());
            mask |= RuleTable.getMask(httpMethod);
        }

        return mask == 0 ? RuleTable.ALL_METHOD_MASK : mask;
    }

    /**
     * 将方法掩码拆分为单个 bit 数组
     * tempMask & -tempMask 利用了“取反加一”会使原数最低位的 1 保持不变, 而该位左侧所有位取反、右侧所有位为 0 的特性, 从而通过按位与运算，仅保留最低位的 1
     * tempMask ^= bit: 去掉该最低位 1 (等价于 tempMask -= bit, 但异或仅在低位唯一时等价)
     *
     * @param methodMask 方法掩码
     * @return 单 bit 数组；ALL 方法返回空数组
     */
    private int[] methodBits(int methodMask) {
        if (methodMask == RuleTable.ALL_METHOD_MASK) {
            return new int[0];
        }

        List<Integer> bits = new ArrayList<>(4);
        int tempMask = methodMask;
        while (tempMask != 0) {
            int bit = tempMask & -tempMask;
            bits.add(bit);
            tempMask ^= bit;
        }

        return bits.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * 编译阶段使用的可变桶结构
     *
     * @param exact   精确路径中间桶
     * @param buckets 动态路径中间桶
     * @param global  全局兜底规则列表
     * @param <T>     业务目标类型
     */
    private record MutableBucket<T>(Map<String, List<MatchRule<T>>> exact,
                                    Map<String, List<MatchRule<T>>> buckets,
                                    List<MatchRule<T>> global) {
        /**
         * 创建空中间桶
         */
        MutableBucket() {
            this(new HashMap<>(), new HashMap<>(), new ArrayList<>());
        }
    }

    /**
     * 规则所属的索引槽位类型
     */
    public enum SlotType {
        /**
         * 精确路径槽位
         */
        EXACT,
        /**
         * 动态路径桶槽位
         */
        BUCKET,
        /**
         * 全局兜底槽位
         */
        GLOBAL
    }

    /**
     * 规则影响范围描述
     *
     * @param slotType   规则所属槽位类型
     * @param key        对应的 exact path 或 bucket key
     * @param methodMask 规则方法掩码
     * @param methodBits 单 bit 方法数组
     */
    public record RuleScope(SlotType slotType, String key, int methodMask, int[] methodBits) {
        /**
         * 判断规则是否适用于所有 HTTP 方法
         *
         * @return {@code true} 表示该规则为全方法规则
         */
        public boolean isAnyMethod() {
            return methodMask == RuleTable.ALL_METHOD_MASK;
        }
    }

    /**
     * 编译后的内部规则对象
     *
     * @param rule       运行期规则
     * @param slotType   所属槽位类型
     * @param key        路径 key
     * @param methodBits 单 bit 方法数组
     * @param <T>        业务目标类型
     */
    private record CompiledRule<T>(MatchRule<T> rule, SlotType slotType, String key, int[] methodBits) {
        /**
         * 判断规则是否适用于所有 HTTP 方法
         *
         * @return {@code true} 表示该规则为全方法规则
         */
        private boolean isAnyMethod() {
            return rule.methodMask() == RuleTable.ALL_METHOD_MASK;
        }
    }
}
