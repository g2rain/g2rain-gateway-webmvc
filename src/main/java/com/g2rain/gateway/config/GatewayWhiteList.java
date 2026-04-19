package com.g2rain.gateway.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * <p>{@code GatewayWhiteList} 是网关白名单配置类，用于定义全局及各 Filter 的白名单规则。</p>
 *
 * <p><b>功能说明：</b></p>
 * <ul>
 *     <li>绑定配置文件中 <code>gatewayWhiteList</code> 前缀的配置内容。</li>
 *     <li>支持全局白名单和针对不同 Filter 的白名单配置。</li>
 *     <li>配合 {@link com.g2rain.gateway.whitelist.WhiteListResolver} 使用，实现动态白名单判断。</li>
 * </ul>
 *
 * <p><b>配置示例（application.yml）：</b></p>
 * <pre>{@code
 * gateway-white-list:
 *   global:
 *     pattern-paths:
 *       - /v2/api-docs
 *   filters:
 *     gateway-auth-filter:
 *       context-paths:
 *         - /auth
 *     cached-body-filter:
 *       context-paths:
 *         - /auth
 *     principal-forward-filter:
 *       context-paths:
 *         - /auth
 *     sign-verification-filter:
 *       context-paths:
 *         - /auth
 *     trace-logging-filter:
 *       context-paths:
 *         - /auth
 *     response-adjust-filter:
 *       context-paths:
 *         - /auth
 * }</pre>
 *
 * <p><b>字段说明：</b></p>
 * <ul>
 *     <li><b>global</b>：全局白名单，适用于所有 Filter。</li>
 *     <li><b>filters</b>：针对各 Filter 单独配置的白名单规则，key 为 Filter 名称。</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/8
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "gateway-white-list")
public class GatewayWhiteList {

    /**
     * 全局白名单规则，适用于所有 Filter。
     */
    private WhiteList global;

    /**
     * 各 Filter 白名单规则，key 为 Filter 名称。
     */
    private Map<String, WhiteList> filters;

    /**
     * 白名单规则实体类，包含 contextPaths、exactPaths、patternPaths 三种匹配方式。
     */
    @Data
    public static class WhiteList {
        /**
         * 上下文路径列表，按完整路径前缀匹配。
         */
        private Set<String> contextPaths;

        /**
         * 精确路径列表，按完全一致匹配。
         */
        private Set<String> exactPaths;

        /**
         * 模式路径列表，支持通配符匹配（如 <code>/&#42;&#42;/*.css</code>）。
         */
        private Set<String> patternPaths;
    }
}
