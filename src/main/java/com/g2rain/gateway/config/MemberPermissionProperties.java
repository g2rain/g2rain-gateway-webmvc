package com.g2rain.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * MEMBER 入口权限模式：{@code enforce}（默认）或 {@code shadow}。
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "gateway.member-permission")
public class MemberPermissionProperties {

    private String mode = "enforce";

    public boolean isShadow() {
        return "shadow".equalsIgnoreCase(mode);
    }

    public boolean isEnforce() {
        return !isShadow();
    }
}
