# 架构偏差与风险

| 事项 | 证据 | 状态 |
| --- | --- | --- |
| 构建依赖无法解析 | `g2rain-infra-api:1.0.0` 不在已配置仓库 | 阻断验证 |
| 开发默认凭据进入版本化配置 | `application.yml` 的 Nacos 默认用户名/密码 | 高风险 |
| 请求/响应日志未脱敏 | `TraceLoggingFilter` 记录完整 Header、query、body 和部分响应 | 高风险 |
| POST 与上传无限制 | Tomcat POST、multipart file/request 均为 `-1` | 资源耗尽风险 |
| 路由全量刷新使用 `putAll` | `GatewayRouteLoader.refresh` 不清理已移除路由 | 可能保留陈旧路由 |
| 安全测试明显不足 | 仅发现 4 个测试类，无过滤器直接测试 | 待补充 |
| 启动注释残留 IAM 包名 | `application.yml` 首行 | 待修复 |
| README 流程图仍写 WebFlux | README 当前内容 | 使用 generate 单独修复 |
| 容器端口与应用默认不同 | Docker `8080`，应用默认 `8083` | 部署需显式统一 |

中央 Profile 仍为 Draft，本项目在上述阻断关闭前保持 `planned`。
