# 模块

| 包 | 职责 |
| --- | --- |
| `config`、`route` | MVC.fn 注册、动态路由编译、快照与虚拟线程配置 |
| `filters` | Servlet 层、路由层和响应后处理安全链 |
| `client` | 基于 g2rain Basis/Infra API 的 OpenFeign 客户端 |
| `cache` | 路由、权限、名称、内部路由与 API Key 缓存同步 |
| `matcher` | HTTP 方法与路径规则编译、版本化匹配缓存 |
| `token`、`codec`、`utils` | 密钥、摘要和 RFC 3986 编码 |
| `controller` | OpenAPI 文档目录 |
| `model`、`exception` | 主体、路由、事件和统一错误模型 |
