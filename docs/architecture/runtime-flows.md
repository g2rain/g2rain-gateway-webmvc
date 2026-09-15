# 运行流程

## 启动路由

应用启动后，`GatewayRouteLoader` 同步调用 Basis 的路由定义和服务注册 API，合并后编译为 MVC.fn `RouterFunction` 并刷新 `RouterFuncHolder`。增量同步继续执行 upsert/remove。

## Servlet 层

1. `EdgePrincipalContextScopeFilter`（+0）建立 ThreadLocal 请求主体上下文。
2. `GlobalErrorFilter`（+100）兜底异常并输出统一 JSON。
3. `CachedBodyFilter`（+200）缓存请求/响应，并调度响应处理器。

## 路由层

`TraceLoggingFilter`（+300）→ `ApiKeyFilter`（+350）→ `GatewayTokenAuthFilter`（+400）→ `GatewayDPoPAuthFilter`（+500）→ `ApiPermissionFilter`（+600）→ `SignVerificationFilter`（+700）→ `PrincipalForwardFilter`（+800）。响应阶段再执行日志与 `ResponseAdjustFilter`（+900）。

顺序、跳过条件和白名单都是安全契约。
