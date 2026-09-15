# g2rain-gateway-webmvc Agent Instructions

项目事实见 `docs/project.yaml`，文档入口见 `docs/index.md`。

## 项目定位

- 中央 Profile：`gateway-service 1.0.0-draft`
- 实现类型：`webmvc`
- 采用状态：planned
- 当前验证：`mvn test` 因 `g2rain-infra-api:1.0.0` 无法解析而失败

本项目以 Spring Cloud Gateway Server WebMVC、MVC.fn、Servlet Filter、OpenFeign 和虚拟线程实现统一入口。它负责动态路由、API Key/JWT/DPoP、API 权限、签名、主体透传、响应处理及缓存同步，不拥有身份、权限、路由或领域主数据。

## 执行规则

- 开始前读取中央 Profile、`docs/project.yaml`、架构偏差、过滤器链、安全和当前需求。
- Servlet 层与 MVC.fn 路由层顺序属于安全协议；修改时同步文档并覆盖成功、失败和绕过测试。
- 阻塞 I/O、Feign、虚拟线程、连接和超时必须有明确资源上限。
- 外部主体头不可信；仅转发验证后重建的主体上下文。
- Gateway API 权限不替代下游领域和数据级授权。
- 路由、权限和令牌上下文由 Basis 等数据所有者提供；内存快照不是主数据源。
- 不记录 Token、Cookie、DPoP、API Key、密码、Secret 或敏感正文；当前实现存在已登记风险。
- 不使用仓库中的开发默认凭据部署共享或生产环境。
- 不修改 README；README 更新使用单独的 `generate` 命令。

## 完成前

先解决内部 API 依赖，再运行 `mvn test`。涉及运行时的变更还需验证 Nacos、Basis、Infra、Redis 同步、IAM 契约和真实下游服务，并按 `docs/development/definition-of-done.md` 报告未验证项。
