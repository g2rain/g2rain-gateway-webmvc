package com.g2rain.gateway.model.context;


import com.g2rain.common.web.ApplicationScope;
import com.g2rain.common.web.PrincipalContext;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Edge 网关上下文信息。
 * <p>
 * 本类继承自 {@link PrincipalContext}，用于存储当前请求在 Edge 网关中的上下文信息，
 * 包括请求的 locale、签名信息等，便于后续过滤器或业务逻辑使用。
 * </p>
 *
 * <h2>主要字段</h2>
 * <ul>
 *     <li>{@link #paramHashStr} — 请求参数签名字符串</li>
 *     <li>{@link #hashAlgorithm} — 请求签名使用的哈希算法</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * EdgePrincipalContext context = EdgePrincipalContext.of();
 * context.setLocale(Locale.ENGLISH);
 * context.setParamHashStr("abcdef123456");
 * context.setHashAlgorithm("SHA-256");
 * }</pre>
 *
 * @author alpha
 * @since 2025/10/6
 */
@Setter
@Getter
@NoArgsConstructor
public class EdgePrincipalContext extends PrincipalContext {

    /**
     * 请求参数签名字符串
     */
    private String paramHashStr;

    /**
     * 请求签名使用的哈希算法
     */
    private String hashAlgorithm;

    /**
     * 请求所属应用编码
     */
    private String applicationCode;

    /**
     * 应用编码集合
     * <p>用于鉴权，标识当前请求归属的应用是否有权限。</p>
     */
    private List<ApplicationScope> applicationScopes;

    /**
     * 工厂方法，创建新的 EdgePrincipalContext 实例
     */
    public static EdgePrincipalContext of() {
        return new EdgePrincipalContext();
    }
}
