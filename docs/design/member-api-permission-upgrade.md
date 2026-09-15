# MEMBER 接口权限校验升级

- 状态：Gateway 已落地（默认 `enforce`）
- 目标仓库：`g2rain-gateway-webmvc`（与 `g2rain-gateway-webflux` 同语义）
- 完整方案原文：姐妹仓 `g2rain-gateway-webflux` 的 `docs/design/member-api-permission-upgrade.md`

## 本仓落地摘要

1. MEMBER **不**再读 `DefaultPerm`；按 Token `organId` 使用 `MemberPerm`。
2. 回源：`AuthorityClient.getSessionApiPermissions(MEMBER, organId)`（Basis Feign）。
3. 同步：`BasisSyncerEnum.MEMBER_PERM`，载荷 `organId`，按机构失效。
4. 配置：`gateway.member-permission.mode`（`enforce` | `shadow`，默认 `enforce`）。
5. 主体：`organId`+`memberId` 必填，且不得混入 `userId`/`passportId`。
6. 回源失败：`GatewayErrorCode.MEMBER_PERM_UNAVAILABLE`，不回退 DefaultPerm。

入口认证分流见 [member-session-gateway.md](member-session-gateway.md)。
