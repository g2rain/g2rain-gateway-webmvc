# 测试

2026-09-06 执行 `mvn test`，在依赖解析阶段失败：无法获取 `com.g2rain:g2rain-infra-api:1.0.0`。因此编译和测试均未执行，不能宣称测试通过。

仓库当前发现 4 个测试类、15 个 `@Test` 方法，覆盖 matcher、RouterFuncHolder 与 RouteSync；未发现 API Key、JWT、DPoP、API 权限、签名、主体透传、日志脱敏、错误过滤和响应调整的直接测试。

依赖恢复后至少运行 `mvn test`，并补充过滤器成功/失败/绕过、伪造主体头、敏感日志、无限请求体、路由删除与外部协作测试。JaCoCo 与静态检查本次均未验证。
