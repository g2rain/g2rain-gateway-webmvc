package com.g2rain.gateway.model.cache;

import java.util.Set;

/**
 * 某机构 ANONYMOUS 入口权限快照。
 *
 * @param organId  机构 ID
 * @param version  Basis 侧权限版本
 * @param apiIds   可访问的 API ID；空集表示机构未开通任何 ANONYMOUS 能力
 * @param entryIds 启用中的接入码 ID；Token 的 entryId 不在其中即视为入口已停用
 */
public record AnonymousOrganPermission(long organId, long version, Set<Long> apiIds, Set<Long> entryIds) {

    /**
     * 接入码仍启用且接口已开通。
     */
    public boolean allows(Long entryId, Long apiId) {
        return entryIds.contains(entryId) && apiIds.contains(apiId);
    }
}
