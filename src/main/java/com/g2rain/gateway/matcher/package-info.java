/**
 * 网关规则匹配子系统（matcher）包。
 *
 * <h2>1. 解决的问题</h2>
 * <p>
 * matcher 用于把“方法 + 路径”请求快速映射到业务目标（target），支持两类典型业务：
 * </p>
 * <ul>
 *     <li>动态路由：根据请求命中对应的路由处理对象</li>
 *     <li>接口权限：根据请求命中对应的权限标识/接口标识</li>
 * </ul>
 *
 * <h2>2. 核心设计思路</h2>
 * <p>
 * 设计分为“编译期一次处理”和“运行期高频只读”两段：
 * </p>
 * <ol>
 *     <li>
 *         编译期：{@link com.g2rain.gateway.matcher.RuleCompiler} 将
 *         {@link com.g2rain.gateway.matcher.RuleDefinition} 预编译为
 *         {@link com.g2rain.gateway.matcher.RuleTable}。
 *     </li>
 *     <li>
 *         运行期：{@link com.g2rain.gateway.matcher.MatchEngine} 基于不可变
 *         {@code RuleTable} 做无锁读取与匹配，并使用本地缓存加速热点请求。
 *     </li>
 * </ol>
 * <p>
 * 这样可以把解析、分桶、排序的成本前置，减少请求线程的实时计算压力。
 * </p>
 *
 * <h2>3. 匹配模型</h2>
 * <ul>
 *     <li>{@link com.g2rain.gateway.matcher.MethodRule}：运行期单条规则，包含 methodMask、PathPattern、target、priority</li>
 *     <li>{@link com.g2rain.gateway.matcher.RuleTable}：不可变三层索引（exact / buckets / global）</li>
 *     <li>{@link com.g2rain.gateway.matcher.MatcherUtils}：路径标准化与首段提取工具</li>
 * </ul>
 *
 * <h2>4. 命中流程（固定顺序）</h2>
 * <ol>
 *     <li>路径标准化（去重斜杠、补前导斜杠、去尾斜杠）</li>
 *     <li>按方法掩码做位运算过滤</li>
 *     <li>按索引顺序尝试：exact -> buckets -> global</li>
 *     <li>桶内按 priority 与路径特异性的排序顺序返回首个命中</li>
 * </ol>
 *
 * <h2>5. 热更新与并发语义</h2>
 * <ul>
 *     <li>{@code MatchEngine#replace(...)} 通过原子替换整表实现热更新</li>
 *     <li>替换后立即清空请求匹配缓存，避免命中旧规则结果</li>
 *     <li>请求线程始终读取一致快照，不会观察到中间态</li>
 * </ul>
 *
 * <h2>6. 建议使用方式</h2>
 * <ol>
 *     <li>准备规则集合：构建 {@code RuleDefinition<T>}</li>
 *     <li>编译规则：{@code RuleTable<T> table = new RuleCompiler<T>().compile(rules)}</li>
 *     <li>注入引擎：{@code engine.replace(table)}</li>
 *     <li>请求匹配：{@code engine.match(method, path)} 或 {@code engine.matchRule(method, path)}</li>
 * </ol>
 */
package com.g2rain.gateway.matcher;

