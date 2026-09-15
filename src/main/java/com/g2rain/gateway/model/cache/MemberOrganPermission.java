package com.g2rain.gateway.model.cache;

import java.util.Set;

/**
 * 某机构 MEMBER 入口 API 权限快照。
 */
public record MemberOrganPermission(
    long organId,
    long version,
    Set<Long> apiIds
) {
}
