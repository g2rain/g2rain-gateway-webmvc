package com.g2rain.gateway.permission;

/**
 * 接口权限判定结果。
 *
 * @param allowed       是否允许访问
 * @param interfaceCode 命中的接口编码（未命中规则时可能为空）
 */
public record ApiPermissionDecision(boolean allowed, String interfaceCode) {
}

