package com.g2rain.gateway.permission;

import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.matcher.MatchEngine;
import com.g2rain.gateway.matcher.RuleCompiler;
import com.g2rain.gateway.matcher.RuleDefinition;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接口权限匹配服务（基于 matcher）
 * <p>
 * 设计目标：
 * <ul>
 *     <li>权限规则统一管理，一份全局规则表</li>
 *     <li>运行期只读匹配，热更新时原子替换</li>
 *     <li>提供 replace/upsert/remove，便于对接消息同步</li>
 * </ul>
 * </p>
 */
@Service
public class ApiPermissionService {

    private final MatchEngine<ApiPermissionRule> matchEngine = new MatchEngine<>();
    private final RuleCompiler<ApiPermissionRule> ruleCompiler = new RuleCompiler<>();
    private volatile Map<Long, ApiPermissionRule> rules = new ConcurrentHashMap<>();

    /**
     * 全量替换权限规则并立即生效。
     *
     * @param newRules 新规则集合
     */
    public synchronized void replace(Collection<ApiPermissionRule> newRules) {
        Collection<ApiPermissionRule> safeRules = Objects.nonNull(newRules) ? newRules : Collections.emptyList();
        Map<Long, ApiPermissionRule> map = new ConcurrentHashMap<>();
        for (ApiPermissionRule rule : safeRules) {
            if (Objects.nonNull(rule) && Objects.nonNull(rule.id())) {
                map.put(rule.id(), rule);
            }
        }

        compileAndReplace(map.values());
        this.rules = map;
    }

    /**
     * 新增或更新单条规则并立即生效。
     *
     * @param rule 规则
     */
    public synchronized void upsert(ApiPermissionRule rule) {
        if (Objects.isNull(rule) || Objects.isNull(rule.id())) {
            return;
        }

        rules.put(rule.id(), rule);
        compileAndReplace(rules.values());
    }

    /**
     * 删除单条规则并立即生效。
     *
     * @param ruleId 规则 ID
     */
    public synchronized void remove(Long ruleId) {
        if (Objects.isNull(ruleId)) {
            return;
        }

        rules.remove(ruleId);
        compileAndReplace(rules.values());
    }

    /**
     * 执行接口权限判定。
     *
     * @param method          请求方法
     * @param path            请求路径
     * @param applicationCode 当前应用编码
     * @param backEndRequest  是否后端请求（后端请求默认放行）
     * @return 判定结果
     */
    public ApiPermissionDecision check(HttpMethod method, String path, String applicationCode, boolean backEndRequest) {
        if (backEndRequest) {
            return new ApiPermissionDecision(true, null);
        }

        var matched = matchEngine.matchRule(method, path);
        // 未配置权限规则时默认放行，避免影响存量链路。
        if (matched.isEmpty()) {
            return new ApiPermissionDecision(true, null);
        }

        ApiPermissionRule rule = matched.get().target();
        Set<String> allowedCodes = Objects.nonNull(rule.allowedCodes()) ? rule.allowedCodes() : Set.of();
        if (allowedCodes.isEmpty()) {
            return new ApiPermissionDecision(true, rule.interfaceCode());
        }

        boolean allowed = Strings.isNotBlank(applicationCode) && allowedCodes.contains(applicationCode);
        return new ApiPermissionDecision(allowed, rule.interfaceCode());
    }

    private void compileAndReplace(Collection<ApiPermissionRule> snapshot) {
        var definitions = snapshot.stream()
            .filter(Objects::nonNull)
            .filter(r -> Strings.isNotBlank(r.path()))
            .map(r -> new RuleDefinition<>(r.methods(), r.path(), r))
            .toList();
        matchEngine.replace(ruleCompiler.compile(definitions));
    }
}

