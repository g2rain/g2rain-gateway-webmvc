# 故障排查

| 现象 | 检查 |
| --- | --- |
| Maven 无法构建 | `g2rain-infra-api:1.0.0` 与 Basis API 是否已发布/安装 |
| 启动路由失败 | Nacos、Basis Feign、路由和服务注册数据 |
| 删除路由仍可访问 | 全量 refresh 的 `putAll` 与同步 delete 是否执行 |
| 认证或权限拒绝 | 凭据分流、白名单、路由 ID、权限缓存、DPoP/摘要 |
| 上传导致内存/线程压力 | 无限 multipart/POST、10MB 自定义限制是否实际覆盖 |
| 容器不可访问 | `SERVER_PORT` 与 `EXPOSE 8080` 是否一致 |

分享日志前删除凭据和敏感正文。
