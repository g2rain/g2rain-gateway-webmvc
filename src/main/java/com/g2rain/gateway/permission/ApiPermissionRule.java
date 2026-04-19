package com.g2rain.gateway.permission;

import java.util.Set;

/**
 * 接口权限规则定义（权限域模型）
 *
 * @param id                      规则唯一标识（用于增量更新/删除）
 * @param methods                 允许的 HTTP 方法集合（如 GET,POST / ALL / *）
 * @param path                    请求路径表达式
 * @param interfaceCode           业务接口编码（用于审计与错误提示）
 * @param allowedCodes            允许访问该接口的编码集合（空集合表示不限制）
 */
public record ApiPermissionRule(Long id, String methods, String path, String interfaceCode, Set<String> allowedCodes) {
}

