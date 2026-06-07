package com.g2rain.gateway.matcher;


import org.springframework.web.util.pattern.PathPattern;

/**
 * 运行期匹配规则
 *
 * <p>该模型是 {@link RuleDefinition} 经 {@link RuleCompiler} 预处理后的产物，
 * 会在运行期直接参与 matcher 的候选匹配</p>
 *
 * @param id         规则唯一标识
 * @param methodMask 规则对应的 HTTP 方法位掩码
 * @param pattern    预编译后的 Spring {@link PathPattern} 路径模式
 * @param target     命中后返回的业务目标对象
 * @param <T>        业务目标对象类型
 * @author alpha
 * @since 2026/4/16
 */
public record MatchRule<T>(Long id, int methodMask, PathPattern pattern, T target) {
}
