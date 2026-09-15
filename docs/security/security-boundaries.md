# 安全边界

- API Key 与 JWT/DPoP 分流认证，但都不能绕过适用的 API 权限。
- `SessionType=MEMBER`：验签 JWT 后写入 `memberId`（`X-MEMBER-ID`），并走与其它会话相同的 DPoP/摘要；入口权限按租户已开通的 MEMBER 控制单元（`MemberPerm`），要求 `organId`+`memberId`；不按 `userId` 走员工权限。专题：[MEMBER 会话入口处理](../design/member-session-gateway.md)、[MEMBER 接口权限](../design/member-api-permission-upgrade.md)。
- 外部主体头不可信；`PrincipalForwardFilter` 先移除全部 `PrincipalHeaders`，再按验证后上下文以 set/replace 重建（含 `X-MEMBER-ID`）；白名单路径同样清除外部主体头。
- 下游继续执行租户、对象和数据级授权。
- 白名单扩大、过滤器顺序和签名变化必须安全评审并增加负向测试。
- Token、Cookie、DPoP、API Key、密码、密钥和敏感正文不得进入仓库或日志。

当前日志会输出完整请求头、query、表单/JSON body 和部分响应，且未脱敏；当前 POST 与 multipart 无大小上限。这两项是发布阻断风险。
