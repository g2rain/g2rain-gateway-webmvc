/**
 * 网关匹配基座包
 *
 * <p>该包提供基于 {@code HTTP Method + Path} 的通用规则匹配能力，服务于动态路由、
 * 接口权限等需要高频请求匹配的场景</p>
 *
 * <h2>核心职责</h2>
 * <ul>
 *     <li>接收规则定义并完成一次性预编译</li>
 *     <li>在运行期基于不可变快照执行无锁读取</li>
 *     <li>通过 exact、bucket、global 三层索引缩小候选范围</li>
 *     <li>支持单条规则增量更新及局部缓存失效</li>
 * </ul>
 *
 * <h2>主要类型</h2>
 * <ul>
 *     <li>{@link com.g2rain.gateway.matcher.RuleDefinition}：编译前的规则输入模型</li>
 *     <li>{@link com.g2rain.gateway.matcher.MatchRule}：编译后的运行期规则模型</li>
 *     <li>{@link com.g2rain.gateway.matcher.RuleCompiler}：负责规则编译、增量插入和删除</li>
 *     <li>{@link com.g2rain.gateway.matcher.RuleTable}：运行期只读规则快照</li>
 *     <li>{@link com.g2rain.gateway.matcher.MatchEngine}：运行期匹配引擎与缓存协调器</li>
 *     <li>{@link com.g2rain.gateway.matcher.MatcherUtils}：路径标准化与分桶工具</li>
 * </ul>
 *
 * <h2>匹配顺序</h2>
 * <ol>
 *     <li>标准化请求路径</li>
 *     <li>按 HTTP 方法选择对应的规则索引</li>
 *     <li>优先尝试 exact 精确路径</li>
 *     <li>其次尝试 bucket 动态路径桶；真实请求用 {@link com.g2rain.gateway.matcher.MatcherUtils#requestBucketKey(String)}
 *     取路径前两段作为分桶 key（与编译期 {@link com.g2rain.gateway.matcher.MatcherUtils#getBucketKey(String)} 分离）</li>
 *     <li>最后尝试 global 兜底规则</li>
 * </ol>
 *
 * <h2>并发语义</h2>
 * <p>请求线程始终读取完整一致的规则快照；更新线程通过原子替换状态或局部版本戳递增，
 * 使运行期不会观察到中间态</p>
 *
 * @author alpha
 * @since 2026/4/16
 */
package com.g2rain.gateway.matcher;
