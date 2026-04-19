package com.g2rain.gateway.matcher;


import org.springframework.web.util.pattern.PathPattern;

/**
 * 预编译后的方法与路径规则
 * <p>
 * 这是运行时匹配阶段使用的内部模型，由 {@link RuleCompiler} 从 {@link RuleDefinition} 转换而来
 * 与输入模型相比，这个类更贴近执行期：
 * <ul>
 *     <li>methods 已经被折叠成 methodMask（位运算可快速过滤）</li>
 *     <li>path 已经被解析为 PathPattern（避免请求时重复 parse）</li>
 *     <li>priority 仍保留用于冲突决策和稳定匹配顺序（由 {@link RuleCompiler} 排序）</li>
 * </ul>
 * </p>
 *
 * @param methodMask HTTP 方法位掩码（如 GET|POST 会合并多个 bit）
 * @param pattern    预编译后的路径模式（用于高频匹配）
 * @param target     命中后返回的业务对象（路由/权限等）
 * @param priority   规则优先级（数值越大越先尝试）
 * @author alpha
 * @since 2026/4/16
 */
public record MethodRule<T>(int methodMask, PathPattern pattern, T target, int priority) {
}
