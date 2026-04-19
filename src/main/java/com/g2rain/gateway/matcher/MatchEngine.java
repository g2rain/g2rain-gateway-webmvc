package com.g2rain.gateway.matcher;


import com.g2rain.common.utils.Strings;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 通用匹配引擎（动态路由 / 接口权限共用基座）
 * <p>
 * 核心职责：
 * <ul>
 *     <li>持有当前生效的 {@link RuleTable}</li>
 *     <li>根据 method + path 在规则表中执行匹配</li>
 *     <li>使用本地缓存加速热点请求的重复匹配</li>
 * </ul>
 * </p>
 *
 * <p>
 * 设计目标：
 * <ul>
 *     <li>高并发读：请求线程无锁读，避免性能抖动</li>
 *     <li>热更新：规则刷新时原子替换表并清空缓存</li>
 *     <li>稳定性：对空值、非法路径、脏数据做防御兜底</li>
 * </ul>
 * </p>
 *
 * @param <T> 命中规则后返回的业务对象类型（可为路由句柄、接口ID、权限标识等）
 * @author alpha
 * @since 2026/4/16
 */
public class MatchEngine<T> {

    /**
     * 默认请求匹配缓存容量
     * <p>
     * 仅缓存 method + path 的匹配结果，不缓存完整请求体，内存可控
     * </p>
     */
    private static final long DEFAULT_CACHE_SIZE = 10_000L;

    /**
     * 缓存访问后过期时间
     * <p>
     * 热点路径会被持续保留；长时间无访问的键会被自动清理，避免低峰期内存长期占用
     * </p>
     */
    private static final Duration DEFAULT_EXPIRE_AFTER_ACCESS = Duration.ofHours(1);

    /**
     * 当前生效规则表的原子引用
     * <p>
     * 刷新规则时整表替换，读线程始终拿到一致快照，不会看到中间态
     * </p>
     */
    private final AtomicReference<RuleTable<T>> tableRef = new AtomicReference<>(RuleTable.empty());

    /**
     * 请求级匹配缓存：
     * key = method + normalizedPath + mask
     * value = 命中规则（或未命中 Optional.empty）
     */
    private final Cache<RequestKey, Optional<MethodRule<T>>> cache;

    /**
     * 默认构造：使用默认缓存容量
     */
    public MatchEngine() {
        this(DEFAULT_CACHE_SIZE);
    }

    /**
     * 自定义缓存容量构造
     *
     * @param maxCacheSize 最大缓存条数，<=0 时自动回退默认值
     */
    public MatchEngine(long maxCacheSize) {
        // 防止错误配置导致缓存行为异常
        long safeSize = maxCacheSize > 0 ? maxCacheSize : DEFAULT_CACHE_SIZE;
        // 匹配缓存策略：
        // 1) maximumSize: 限制缓存上限，防止键空间过大导致内存无限增长；
        // 2) expireAfterAccess: 访问后过期，低峰期会自然淘汰冷数据（例如夜间）；
        // 3) 二者组合用于平衡命中率与内存占用
        this.cache = Caffeine.newBuilder()
            .maximumSize(safeSize)
            .expireAfterAccess(DEFAULT_EXPIRE_AFTER_ACCESS)
            .build();
    }

    /**
     * 对外暴露的简化匹配接口：仅返回业务 target
     *
     * @param method 请求方法
     * @param path   请求路径
     * @return 命中的业务目标；未命中返回 empty
     */
    public Optional<T> match(HttpMethod method, String path) {
        return matchRule(method, path).map(MethodRule::target);
    }

    /**
     * 对外暴露的完整匹配接口：返回命中的完整规则
     * <p>
     * 适用于需要读取规则元信息（priority、methodMask、pattern）的场景，
     * 例如权限诊断、命中链路日志、冲突规则排查等
     * </p>
     *
     * @param method 请求方法
     * @param path   请求路径
     * @return 命中规则；未命中返回 empty
     */
    public Optional<MethodRule<T>> matchRule(HttpMethod method, String path) {
        // 入参防御：空方法、空路径、空白路径直接视为未命中
        if (Objects.isNull(method) || Strings.isBlank(path)) {
            return Optional.empty();
        }

        // 先做路径标准化，避免 "/a//b/" 与 "/a/b" 形成多份缓存键
        String normalizedPath = MatcherUtils.normalize(path);
        RequestKey key = new RequestKey(method, normalizedPath);

        // 使用 Caffeine 原子加载，避免并发下重复计算
        return cache.get(key, this::computeMatch);
    }

    /**
     * 在规则数组中顺序查找首个命中规则
     *
     * @param arr  规则数组（可能为 null）
     * @param mask 请求方法掩码
     * @param path 预解析路径容器
     * @return 首个命中规则；未命中返回 empty
     */
    private Optional<MethodRule<T>> find(MethodRule<T>[] arr, int mask, PathContainer path) {
        if (Objects.isNull(arr)) {
            return Optional.empty();
        }

        for (var r : arr) {
            // 先做方法位过滤，再做路径匹配，减少 pattern.matches 调用次数
            if (Objects.nonNull(r) && (r.methodMask() & mask) != 0 && r.pattern().matches(path)) {
                return Optional.of(r);
            }
        }

        return Optional.empty();
    }

    /**
     * 匹配缓存键
     * <p>
     * 预先缓存 mask，避免每次缓存命中后再次计算方法位
     * </p>
     *
     * @param method 请求方法
     * @param path   规范化后的请求路径
     * @param mask   方法掩码
     */
    private record RequestKey(HttpMethod method, String path, int mask) {

        /**
         * 便捷构造：根据 method 自动计算 mask
         */
        private RequestKey(HttpMethod method, String path) {
            this(method, path, RuleTable.getMask(method));
        }
    }

    /**
     * 原子替换规则表并清空缓存
     * <p>
     * 这是热更新入口：调用后新请求会基于新表重新匹配，旧缓存结果不会残留
     * </p>
     *
     * @param newTable 新规则表；传 null 时自动回退为空表
     */
    public void replace(RuleTable<T> newTable) {
        // null 防御，避免外部调用误传导致 NPE
        tableRef.set(Objects.nonNull(newTable) ? newTable : RuleTable.empty());
        // 表切换后必须清缓存，防止命中旧规则结果
        cache.invalidateAll();
    }

    /**
     * 实际匹配计算逻辑（供缓存 loader 调用）
     * <p>
     * 匹配顺序固定为：
     * <ol>
     *     <li>exact：精确路径</li>
     *     <li>buckets：首段分桶</li>
     *     <li>global：全局兜底</li>
     * </ol>
     * </p>
     *
     * @param key 请求缓存键
     * @return 命中规则；未命中返回 empty
     */
    private Optional<MethodRule<T>> computeMatch(RequestKey key) {
        RuleTable<T> table = tableRef.get();

        PathContainer container;
        try {
            // 路径在此阶段转为 PathContainer，供 PathPattern 高效匹配
            container = PathContainer.parsePath(key.path());
        } catch (Exception ignored) {
            // 非法路径不应影响网关线程，直接视为未命中
            return Optional.empty();
        }

        /*
         * 第一层：精确路径索引（命中成本最低，优先尝试）
         * 第二层：按首段分桶的动态规则（控制候选数量，避免全表扫描）
         * 第三层：全局兜底规则（如 /**），用于覆盖无法被前两层命中的请求
         */
        return find(table.exact().get(key.path()), key.mask(), container)
            .or(() -> find(table.buckets().get(MatcherUtils.firstSegment(key.path())), key.mask(), container))
            .or(() -> find(table.global(), key.mask(), container));
    }
}
