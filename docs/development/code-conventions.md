# 代码约定

- 区分 Servlet Filter、MVC.fn HandlerFilterFunction 与 ResponseBodyProcessor 生命周期。
- 阻塞 Feign 调用配置连接、读取超时和资源上限；虚拟线程不是无限资源。
- 新过滤器记录顺序、前置条件、白名单、错误码和负向测试。
- ThreadLocal 主体上下文必须按请求建立并在 finally 清理。
- 路由与缓存同步定义全量、增量、幂等、删除和并发语义。
- 敏感 Header/body 默认不记录；跨仓库 API 与消息变化同步文档。
