package com.g2rain.gateway.matcher;


/**
 * 规则定义（编译前的输入模型）
 * <p>
 * 这个类的职责非常单一：承载“外部规则数据”，交给 {@link RuleCompiler} 做预编译
 * 它本身不做匹配、不做排序；字段级容错（如非法 methods、非法 path）由编译器统一处理。
 * </p>
 *
 * <p>
 * 典型使用场景：
 * <ul>
 *     <li>动态路由：target 可放路由处理器句柄、路由配置对象等</li>
 *     <li>接口权限：target 可放 interfaceId、permissionCode、policyId 等</li>
 * </ul>
 * </p>
 *
 * <p>
 * methods 支持：
 * <ul>
 *     <li>单方法: GET</li>
 *     <li>多方法: GET,POST</li>
 *     <li>通配: ALL / *</li>
 * </ul>
 * </p>
 *
 * @param methods  规则允许的 HTTP 方法集合（字符串形式，供编译器解析为位掩码）
 * @param path     路径表达式（支持静态路径、模板变量、通配符）
 * @param target   命中后要返回的业务对象（路由目标/权限标识等）
 * @param priority 规则优先级（值越大优先级越高，用于冲突时稳定决策）
 * @author alpha
 * @since 2026/4/16
 */
public record RuleDefinition<T>(String methods, String path, T target, int priority) {

    /**
     * 便捷构造：不显式传优先级时，默认优先级为 0
     * <p>
     * 这个默认值的意义是“中性优先级”，适合大多数无冲突场景
     * 只有在同一路径存在多条规则且需要明确覆盖关系时，才需要显式传 priority
     * </p>
     *
     * @param methods HTTP 方法集合
     * @param path    路径表达式
     * @param target  业务目标对象
     */
    public RuleDefinition(String methods, String path, T target) {
        // 将默认优先级统一收敛到主构造，保证行为一致且便于维护。
        this(methods, path, target, 0);
    }
}
