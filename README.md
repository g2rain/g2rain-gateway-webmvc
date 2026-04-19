# G2rain Gateway WebMVC

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-437291?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.1-586069?logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

基于 **Spring Cloud Gateway Server WebMVC** 的统一入口：`RouterFuncHolder` + `RouteCompiler` 将 **g2rain-infra** 下发的 **`RouteDefinitionVo`** 编译为 **`RouterFunction`** 并驻留内存，经 **Nacos** 注册发现由网关转发至下游；**OpenFeign** 调用 **infra / basis** 拉取路由、国际化与侧车数据；集成 **JWT / DPoP** 校验、签名校验、主体作用域、链路日志与统一异常处理；通过 **Spring Cloud Stream（Redis）** 消费 `g2rain-syncer` 以增量刷新路由与相关缓存；默认启用 **虚拟线程**（`spring.threads.virtual.enabled: true`）。

本项目由 **[谷雨开源](https://g2rain.com)**（G2Rain）社区维护，采用 **Apache License 2.0** 开源协议发布。

---

## 目录

- [功能概览](#功能概览)
- [技术栈](#技术栈)
- [与周边服务的关系](#与周边服务的关系)
- [全局过滤器顺序](#全局过滤器顺序)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [OpenAPI / Swagger UI](#openapi--swagger-ui)
- [构建与镜像](#构建与镜像)
- [代码质量与测试](#代码质量与测试)
- [参与贡献](#参与贡献)
- [许可证](#许可证)

---

## 功能概览

| 能力 | 说明（以当前代码为准） |
|------|------------------------|
| **动态路由** | `RouteCompiler` 在单例就绪后通过 **`RouteDefinitionClient`**（Feign → **g2rain-infra**）加载 **`RouteDefinitionVo`**，编译写入 `RouterFuncHolder`；**不依赖本仓库内的 MySQL/R2DBC**。 |
| **路由增量同步** | `RouteSync` 订阅缓存同步消息（数据源与 **`InfraSyncerEnum.ROUTE_DEFINE`** 一致），对单条路由调用 `RouteCompiler` 的增删改。 |
| **认证** | `GatewayTokenAuthFilter`（JWT）、`GatewayDPoPAuthFilter`（DPoP）；密钥材料可通过 Nacos 的 **`g2rain-token-keypair.yml`**（`group=g2rain`）与本服务配置一并导入。 |
| **签名校验** | `SignVerificationFilter` 结合 `RFC3986` / `PercentCodec` / `HashAlgorithm` 等工具做请求规范与哈希校验。 |
| **主体与响应** | `EdgePrincipalContextScopeFilter` 管理请求级上下文；`GatewayTokenAuthFilter` 等写入 `EdgePrincipalContext`；`PrincipalForwardFilter`、`ResponseAdjustFilter`（含 `ResponseBodyProcessor`）处理透传与响应形态。 |
| **请求体缓存** | Servlet `CachedBodyFilter` 包装请求/响应，便于后续过滤器重复读取 Body。 |
| **日志** | `TraceLoggingFilter` 输出结构化日志（如 `JsonLog`），并与 Micrometer **Baggage / MDC**（`requestId`）配合。 |
| **接口权限** | `ApiPermissionFilter` 依赖 `ApiPermissionService`（matcher 规则表）；当前类上 **`@Component` 被注释**，默认**未**装入路由 **`HandlerFilterFunction`** 链，启用时需自行打开注解并保证 Bean 完整。 |
| **下游调用** | **`RouteDefinitionClient`**、**`ErrorMessageClient`** → **g2rain-infra**；**`OrganClient`**、**`ApplicationClient`** → **g2rain-basis**。 |
| **可选 Kafka 日志** | `KafkaLogSender` 等；默认 **`spring.kafka.enabled: false`**，避免未配置 `bootstrap-servers` 时误连集群。 |
| **可观测性** | Actuator（`health`、`info`）；OpenTelemetry + Micrometer Tracing（`application.yml` 中 OTLP 导出默认关闭，可按环境打开）。 |

---

## 技术栈

| 类别 | 说明 |
|------|------|
| 运行时 | Java **25**（Enforcer 要求 `[25,)`） |
| 网关 | **spring-cloud-starter-gateway-server-webmvc**、**LoadBalancer**、**Nacos** Discovery + Config |
| Web | **Spring MVC.fn**（`RouterFunction` / `ServerRequest`）、**Tomcat**、**虚拟线程**（默认开启） |
| 远程调用 | **OpenFeign**（**g2rain-starter-feign-plus**） |
| 安全与协议 | **Nimbus JOSE JWT** |
| 本地缓存 | **Caffeine**（如组织名、应用名等侧车缓存，见 `cache` 包） |
| 消息 | **Spring Cloud Stream**（Redis Binder，`input` → `g2rain-syncer`）；可选 **spring-kafka** |
| 公共库 | **g2rain-common**；**g2rain-infra-api**、**g2rain-basis-api**；**g2rain-starter-cache-sync**、**g2rain-starter-stream-redis** |
| 文档 | **springdoc-openapi-starter-webmvc-ui**（与 `OpenApiController` 提供的 `/swagger-ui-config` 配合） |

---

## 与周边服务的关系

```text
客户端 → g2rain-gateway (本服务) → lb://下游微服务
              ↓ OpenFeign
         g2rain-infra（路由定义、部分基础数据）
         g2rain-basis（业务支撑 API）
```

路由元数据以 **infra** 为权威来源；增量刷新经 **Redis Stream**（`g2rain-syncer`）由 **`RouteSync`** 驱动。网关本身**不内置**「本地 `route_definition` 表 + R2DBC」方案（若环境中有表，那是 **infra/basis** 侧持久化，由对应服务暴露 API）。

---

## 全局过滤器顺序

WebMVC 实现分为两层：**Servlet**（`OncePerRequestFilter`）与 **路由级**（`HandlerFilterFunction`，由 `RouteCompiler` 组装）。数值越小越靠前（`Ordered.HIGHEST_PRECEDENCE + offset`）。

**Servlet 过滤器**

| Order 偏移 | 类名 | 职责摘要 |
|------------:|------|----------|
| `HIGHEST_PRECEDENCE` | `EdgePrincipalContextScopeFilter` | 绑定 / 清理 `EdgePrincipalContext` 请求作用域 |
| +100 | `GlobalErrorFilter` | 统一异常兜底，输出 `Result` JSON（HTTP 200） |
| +200 | `CachedBodyFilter` | 缓存请求体 / 响应体包装 |

**路由级过滤器链**

| Order 偏移 | 类名 | 职责摘要 |
|------------:|------|----------|
| +300 | `TraceLoggingFilter` | 追踪日志 |
| +400 | `GatewayTokenAuthFilter` | JWT 校验 |
| +500 | `GatewayDPoPAuthFilter` | DPoP 校验 |
| +600 | `ApiPermissionFilter` | 接口权限（默认未注册为 Bean，见功能概览） |
| +700 | `SignVerificationFilter` | 签名校验 |
| +800 | `PrincipalForwardFilter` | 身份头转发 |
| +900 | `ResponseAdjustFilter` | 响应调整 |

白名单配置前缀为 **`gateway-white-list`**（`GatewayWhiteList`）；各 Filter 子配置的 **key 为 Filter 类简单类名**（如 `GatewayTokenAuthFilter`），详见 `WhiteListResolver` 与 `GatewayWhiteList` 内注释示例。

---

## 环境要求

- **JDK 25+**
- **Maven 3.9+**（推荐）
- **Nacos**、**Redis**（Stream / 缓存同步）
- 可解析到的 **g2rain-infra**、**g2rain-basis** 等下游服务实例

---

## 快速开始

```bash
git clone <你的仓库克隆地址>
cd g2rain-gateway-webmvc
mvn clean package -DskipTests
java -jar target/g2rain-gateway-webmvc-1.0.0.jar
```

或使用：

```bash
mvn spring-boot:run
```

- 默认 HTTP 端口：**8083**（`SERVER_PORT`，见 `src/main/resources/application.yml`）。
- 注册到 Nacos 的 **`spring.application.name`** 为 **`g2rain-gateway`**（与 artifact 名 `g2rain-gateway-webmvc` 不同，排查注册列表时请注意）。
- 版本以 **`${revision}`** 解析后的 **`pom.xml` 版本**为准（当前默认 **1.0.0**）。

---

## 配置说明

| 项 | 说明 |
|----|------|
| `SERVER_PORT` | 默认 **8083** |
| `NACOS_SERVER_ADDR`、`SPRING_CLOUD_NACOS_*` | 注册与配置中心 |
| `spring.config.import` | 可选：`g2rain-gateway.yml`、`g2rain-token-keypair.yml` |
| `spring.cloud.stream` | Redis Binder，`input` 绑定 **`g2rain-syncer`** |
| `spring.kafka.enabled` | 默认 **false**；启用时需在 Nacos 等环境补全 **Kafka** 连接与序列化配置 |
| `gateway-white-list` | 全局与各 Filter 白名单（见上文） |
| `gateway.limits.request-body-max-size` | 网关侧请求体上限（默认 **10MB**） |
| `springdoc.swagger-ui.config-url` | 指向 **`/swagger-ui-config`**（由 `OpenApiController` 提供） |

---

## OpenAPI / Swagger UI

- `OpenApiController` 基于当前路由聚合各服务的 **OpenAPI** 文档 URL，供 Swagger UI 多服务切换。
- `application.yml` 中已配置 `springdoc.swagger-ui.config-url: /swagger-ui-config` 等项。

---

## 构建与镜像

```bash
mvn clean package
# Jib 示例（需配置镜像仓库权限）
mvn compile jib:dockerBuild
```

Jib 目标镜像：`g2rain/g2rain-gateway-webmvc:${project.version}`，基础镜像 `eclipse-temurin:25-jre`，`pom` 中示例暴露端口 **8080**；**实际监听端口** 仍以 `SERVER_PORT` / Nacos 配置为准。

---

## 代码质量与测试

`pom.xml` 集成 **Enforcer**、**Checkstyle（Google）**、**PMD**、**SpotBugs**、**JaCoCo**。仓库含针对编解码、路由模型、全局异常、过滤器等的 **单元测试**。

```bash
mvn test
mvn jacoco:report
```

---

## 参与贡献

欢迎 Issue 与 Pull Request。若改动影响认证、签名或路由刷新语义，请在 PR 中说明兼容性与回滚方式。

---

## 许可证

**Apache License, Version 2.0**，见 [LICENSE](LICENSE)。

```
Copyright © 2025 g2rain.com
```

---

## 链接

- **组织**：谷雨开源（G2Rain）
- **官网**：<https://www.g2rain.com>
- 将 `<你的仓库克隆地址>` 替换为实际 Git 托管地址。
