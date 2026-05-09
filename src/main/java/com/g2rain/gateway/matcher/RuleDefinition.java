package com.g2rain.gateway.matcher;


/**
 * 规则定义
 *
 * <p>该模型表示 matcher 在编译前接收的规则输入，既可用于动态路由，
 * 也可用于接口权限等其他基于「HTTP 方法 + 路径」的匹配场景</p>
 *
 * @param id      规则唯一标识，用于增量更新、删除及运行期命中标识
 * @param methods 规则支持的 HTTP 方法集合，支持单值、多值及 {@code ALL/*}
 * @param path    规则路径表达式，支持静态路径、模板变量与通配符
 * @param target  规则命中后返回的业务目标对象
 * @param <T>     业务目标对象类型
 * @author alpha
 * @since 2026/4/16
 */
public record RuleDefinition<T>(Long id, String methods, String path, T target) {
}
