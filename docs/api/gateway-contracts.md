# Gateway 契约

- `GET /swagger-ui-config`：从 Basis 服务注册生成 Swagger UI 目录。
- `GET /actuator/health`、`GET /actuator/info`：基础观测端点。
- 其他路径由运行时路由定义产生。

入口支持静态 API Key 或 JWT/DPoP 分流，再执行 API 权限和请求摘要校验。外部主体头不可信；`PrincipalForwardFilter` 只转发验证后重建的最小身份。下游领域授权不能省略。
