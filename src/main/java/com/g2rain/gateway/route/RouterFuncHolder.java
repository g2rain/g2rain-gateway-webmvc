package com.g2rain.gateway.route;

import com.g2rain.gateway.matcher.MatchEngine;
import com.g2rain.gateway.matcher.RuleCompiler;
import com.g2rain.gateway.matcher.RuleDefinition;
import com.g2rain.gateway.matcher.RuleTable;
import com.g2rain.gateway.utils.Constants;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态路由运行期持有器：保存「已编译的 {@link RouterFunction}」与 {@link MatchEngine} 内路径索引的一致性。
 *
 * <p>与网关 WebFlux 实现中的「路径索引持有器」角色类似，但本类中
 * {@link RuleDefinition} 的泛型目标为 {@code RouterFunction<ServerResponse>} 而非路由 id：
 * 粗筛命中后直接对目标函数做 {@link RouterFunction#route}，无需再经 Spring Cloud Gateway 的
 * {@code Route} 抽象。</p>
 *
 * <p>所有写路径（{@link #replace}、{@link #upsert}、{@link #remove}）均 {@code synchronized}，
 * 与 {@link GatewayRouteLoader} 的同步刷新配合，避免请求线程看到半更新的引擎表。</p>
 *
 * @author alpha
 * @since 2026/2/23
 */
@Component
public class RouterFuncHolder {
    /**
     * 运行期匹配引擎
     *
     * <p>负责按请求方法与路径执行候选粗筛, 并维护请求级缓存</p>
     */
    private final MatchEngine<RouterFunction<ServerResponse>> engine = new MatchEngine<>(20_000);

    /**
     * 路由规则编译器
     */
    private final RuleCompiler<RouterFunction<ServerResponse>> compiler = new RuleCompiler<>();

    /**
     * 路由定义注册表
     *
     * <p>该结构保存当前生效的规则定义, 用作单条增量更新时的真相源</p>
     */
    private final Map<Long, RuleDefinition<RouterFunction<ServerResponse>>> routeRegistry = new ConcurrentHashMap<>();

    /**
     * 根据当前请求匹配应执行的 MVC.fn 处理器。
     *
     * <p>两段式：① {@link MatchEngine} 按 HTTP 方法与路径字符串选出有序候选
     * {@link RuleDefinition}；② 对每条候选的 {@code rule.target()}（{@code RouterFunction}）
     * 调用 {@link RouterFunction#route(ServerRequest)}，首个返回非空
     * {@link Optional}{@code <HandlerFunction>} 的候选获胜。</p>
     *
     * <p>命中后把规则主键写入 {@link Constants#ROUTE_INTERNAL_ID}，便于下游过滤器或日志关联。</p>
     *
     * @param request MVC.fn 封装的当前请求（含方法与 path）
     * @return 命中的 {@link HandlerFunction}；无匹配或所有候选拒绝时为空
     */
    public Optional<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        return engine.matchAndVisit(request.method(), request.path(), rule ->
            rule.target().route(request).map(handler -> {
                request.servletRequest().setAttribute(
                    Constants.ROUTE_INTERNAL_ID, rule.id()
                );

                return handler;
            })
        );
    }

    /**
     * 新增或更新单条路由：先移除同 id 旧规则在引擎表中的槽位，再插入新规则，并 bump 相关 scope 版本。
     *
     * @param route 编译后的规则；id 为空则静默忽略
     */
    public synchronized void upsert(RuleDefinition<RouterFunction<ServerResponse>> route) {
        if (Objects.isNull(route) || Objects.isNull(route.id())) {
            return;
        }

        var previous = routeRegistry.put(route.id(), route);
        engine.update(table -> {
            var next = table;
            if (Objects.nonNull(previous)) {
                next = compiler.remove(next, previous);
            }

            return compiler.upsert(next, route);
        }, changedScopes(previous, route));
    }

    /**
     * 删除单条路由：从注册表移除并调用 {@link MatchEngine#update} 删除对应槽位规则。
     *
     * @param routeId 路由主键；null 时忽略
     */
    public synchronized void remove(Long routeId) {
        if (Objects.isNull(routeId)) {
            return;
        }

        var removed = routeRegistry.remove(routeId);
        if (Objects.isNull(removed)) {
            return;
        }

        engine.update(table -> compiler.remove(table, removed),
            changedScopes(removed, null)
        );
    }

    /**
     * 全量替换当前路由快照：清空注册表后用集合重建，并 {@link MatchEngine#replace} 重置引擎与请求缓存。
     *
     * @param definitions 新全量集合，允许为 null（视为仅清空）
     */
    public synchronized void replace(Collection<RuleDefinition<RouterFunction<ServerResponse>>> definitions) {
        routeRegistry.clear();

        if (Objects.nonNull(definitions)) {
            definitions.forEach(d -> routeRegistry.put(d.id(), d));
        }

        engine.replace(compiler.compile(routeRegistry.values()));
    }

    /**
     * 返回当前活跃路由数量
     *
     * @return 活跃路由数量
     */
    public int size() {
        return routeRegistry.size();
    }

    /**
     * 计算一次增量更新影响的 scope 集合
     *
     * @param previous 旧规则
     * @param current  新规则
     * @return 受影响的 scope 集合
     */
    private Collection<MatchEngine.ScopeVersion> changedScopes(RuleDefinition<RouterFunction<ServerResponse>> previous,
                                                               RuleDefinition<RouterFunction<ServerResponse>> current) {
        List<MatchEngine.ScopeVersion> scopes = new java.util.ArrayList<>();
        if (Objects.nonNull(previous)) {
            compiler.describe(previous).ifPresent(scope -> appendScope(scopes, scope));
        }

        if (Objects.nonNull(current)) {
            compiler.describe(current).ifPresent(scope -> appendScope(scopes, scope));
        }

        return scopes;
    }

    /**
     * 将规则影响范围追加到 scope 列表
     *
     * @param scopes scope 集合
     * @param scope  规则影响范围
     */
    private void appendScope(Collection<MatchEngine.ScopeVersion> scopes, RuleCompiler.RuleScope scope) {
        if (scope.isAnyMethod()) {
            scopes.add(toScopeVersion(RuleTable.ALL_METHOD_MASK, scope));
            return;
        }

        for (int bit : scope.methodBits()) {
            scopes.add(toScopeVersion(bit, scope));
        }
    }

    /**
     * 将编译器 scope 描述转换为引擎 scope 版本键
     *
     * @param scopeMethod 方法位
     * @param scope       规则影响范围
     * @return 引擎版本 scope
     */
    private MatchEngine.ScopeVersion toScopeVersion(int scopeMethod, RuleCompiler.RuleScope scope) {
        return switch (scope.slotType()) {
            case EXACT -> MatchEngine.ScopeVersion.exact(scopeMethod, scope.key());
            case BUCKET -> MatchEngine.ScopeVersion.bucket(scopeMethod, scope.key());
            case GLOBAL -> MatchEngine.ScopeVersion.global(scopeMethod);
        };
    }
}
