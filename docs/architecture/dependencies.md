# 依赖

- Basis：路由定义、服务注册、权限、名称映射和静态访问令牌上下文。
- Infra：国际化错误消息；当前构建需要 `g2rain-infra-api:1.0.0`。
- IAM：JWT、DPoP、客户端和密钥协议事实。
- Syncer/Redis：路由与缓存增量消息。
- 下游服务：接收验证后的主体，但继续独立授权。

这些内部 API 坐标必须先发布或安装到开发环境可访问的 Maven 仓库。本次 `mvn test` 因 Infra API 坐标缺失而失败。
