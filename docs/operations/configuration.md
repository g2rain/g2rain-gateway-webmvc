# 配置

主要配置包括 `SERVER_PORT`、`SPRING_PROFILES_ACTIVE`、`NACOS_SERVER_ADDR`、Nacos discovery/config 凭据与命名空间、`gateway-white-list`、`token`、Kafka 开关和地址、`gateway.limits.request-body-max-size`。

当前配置启用虚拟线程，Gateway 自定义请求体上限为 `10MB`，但 Tomcat POST 和 multipart 上限均为无限制，必须统一治理。版本库还包含开发默认 Nacos 凭据，生产和共享环境必须覆盖，后续应移除默认值。
