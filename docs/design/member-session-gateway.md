# SessionType=MEMBER 入口处理

本文记录 WebMVC 网关对客服会员会话（`SessionType=MEMBER`）的入口认证与转发契约。与员工 `USER`、账号 `PASSPORT` 分轨；签发方为 IAM（`POST /auth/member/token`），会员主数据在 Member。

WebFlux 对等说明见姐妹仓库 `g2rain-gateway-webflux` 的同名文档；两边语义必须保持一致。权限细则见 [member-api-permission-upgrade.md](member-api-permission-upgrade.md)。

协议对齐总方案见 IAM [member-token-issuance-alignment.md](https://github.com/g2rain/g2rain-iam/blob/main/docs/design/member-token-issuance-alignment.md)（相对路径以本仓为准时可对照中央/IAM 文档）。

## 1. 目标

| 能力 | 行为 |
| --- | --- |
| JWT 校验 | `GatewayTokenAuthFilter` 验签后写入 `sessionType`、`organId`、`memberId`、scopes、绑钥等；**不**把会员写入 `userId`/`passportId`；须 `OrganType.isTenant`、正数 `organId`/`memberId` |
| DPoP | `GatewayDPoPAuthFilter` 与其它会话**相同**：校验 Proof、绑钥、`acd`∈scopes，写入正数 `applicationId`/`applicationOrganId`；缺失或非法则拒绝 |
| 请求摘要 | `SignVerificationFilter` 与其它会话**相同**（依赖 DPoP 写入的摘要上下文） |
| 入口 API 权限 | `ApiPermissionFilter`：默认 `enforce` 下按 `MemberPerm(organId)`；**不**查 `UserPerm`，不回退 `DefaultPerm` |
| 下游透传 | `PrincipalForwardFilter` 先清全部 `PrincipalHeaders` 再 set 重建值（含 `X-MEMBER-ID`）；白名单也清外部主体头；移除敏感认证头 |

## 2. 过滤器分流

```text
JWT 验签成功（含 MEMBER 失败关闭校验）
  → DPoP（绑钥 + acd→applicationId）
  → Sign（query/body 摘要）
  → MemberPerm(organId) ⊇ apiId
  → 转发 X-SESSION-TYPE / X-ORGAN-ID / X-MEMBER-ID / 应用上下文 …
```

不再因 `SessionType=MEMBER` 跳过 DPoP 或摘要。权限模型（MemberPerm）与协议（DPoP/摘要）分轨。

## 3. 安全约束

- 外部主体头不可信；先移除全部 `PrincipalHeaders`，再转发验证后重建的上下文；白名单路径同样清除外部主体头。
- MEMBER 不得按员工 `userId`/角色做入口鉴权。
- 未开通相关 MEMBER 控制单元的租户不得放行。
- 无 scopes、无绑钥、`acd` 无匹配、非法 `applicationId`/`applicationOrganId`：在转发前拒绝，不得透传空应用上下文。
- 下游继续执行租户与会员级授权（`PrincipalContextHolder.getMemberId()`）。
- `GatewayEvent` 可记录 `sessionType`/`memberId`，不得记录原始 Token。

## 4. 实现锚点

- `GatewayTokenAuthFilter`、`GatewayDPoPAuthFilter`、`ApiPermissionFilter`、`MemberPerm`、`SignVerificationFilter`、`PrincipalForwardFilter`
- [security-boundaries.md](../security/security-boundaries.md)
- [runtime-flows.md](../architecture/runtime-flows.md)
- [member-api-permission-upgrade.md](member-api-permission-upgrade.md)
