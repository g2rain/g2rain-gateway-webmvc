# SessionType=MEMBER 入口处理



本文记录 WebMVC 网关对客服会员会话（`SessionType=MEMBER`）的入口认证与转发契约。与员工 `USER`、账号 `PASSPORT` 分轨；签发方为 IAM（`POST /auth/member/token`），会员主数据在 Member。



WebFlux 对等说明见姐妹仓库 `g2rain-gateway-webflux` 的同名文档；两边语义必须保持一致。权限细则见 [member-api-permission-upgrade.md](member-api-permission-upgrade.md)。



## 1. 目标



| 能力 | 行为 |

| --- | --- |

| JWT 校验 | `GatewayTokenAuthFilter` 验签后写入 `sessionType`、`organId`、`memberId`；**不**把会员写入 `userId` |

| DPoP | `GatewayDPoPAuthFilter` 对 MEMBER **跳过** |

| 请求摘要 | `SignVerificationFilter` 对 MEMBER **跳过** |

| 入口 API 权限 | `ApiPermissionFilter`：默认 `enforce` 下按 `MemberPerm(organId)`；**不**查 `UserPerm`，不回退 `DefaultPerm` |

| 下游透传 | `PrincipalForwardFilter` 先清全部 `PrincipalHeaders` 再 set 重建值（含 `X-MEMBER-ID`）；白名单也清外部主体头；移除敏感认证头 |



## 2. 过滤器分流



```text

JWT 验签成功

  → sessionType == MEMBER ?

       是 → 跳过 DPoP / Sign

            → MemberPerm(organId) ⊇ apiId

            → 转发 X-SESSION-TYPE / X-ORGAN-ID / X-MEMBER-ID …

       否 → 既有 USER / PASSPORT / 静态 Key 路径

```



## 3. 安全约束



- 外部主体头不可信；先移除全部 `PrincipalHeaders`，再转发验证后重建的上下文；白名单路径同样清除外部主体头。

- MEMBER 不得按员工 `userId`/角色做入口鉴权。

- 未开通相关 MEMBER 控制单元的租户不得放行。

- 下游继续执行租户与会员级授权。

- `GatewayEvent` 可记录 `sessionType`/`memberId`，不得记录原始 Token。



## 4. 实现锚点



- `GatewayTokenAuthFilter`、`GatewayDPoPAuthFilter`、`ApiPermissionFilter`、`MemberPerm`、`SignVerificationFilter`、`PrincipalForwardFilter`

- [security-boundaries.md](../security/security-boundaries.md)

- [runtime-flows.md](../architecture/runtime-flows.md)

- [member-api-permission-upgrade.md](member-api-permission-upgrade.md)


