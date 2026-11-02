# 部署与 GHCR 发布

## 前提与边界

本服务是 G2rain（谷雨）开源智能体平台中的 WebMVC 统一入口网关实现，不是整个平台或业务主数据服务。当前仅计划接入 `gateway-service 1.0.0-draft`；实际部署仍需 Nacos、Redis、Basis、Infra、IAM 契约、同步消息和真实下游服务。

Maven 项目版本的事实源是根 `pom.xml` 的 `revision`，当前为 `1.0.0`。运行时为 Java 25、Spring Boot 4.1.2 和 Spring Cloud 2025.1.4；默认监听 `8083`，主类为 `com.g2rain.gateway.Application`。

## 本地构建与运行

```bash
mvn clean package
java -jar target/g2rain-gateway-webmvc-1.0.0.jar
```

这只构建和启动本地 Jar；不等同于容器构建、GHCR 推送、匿名拉取、在线演示或真实部署验证。运行时应通过环境变量或受管配置覆盖 Nacos 地址、用户名、密码、命名空间、令牌密钥以及生产所需配置。

## 本地容器构建与运行

本仓库以 `Dockerfile` 作为容器发布的唯一构建方式；本地和 CI 复用同一基础镜像、Jar、入口和构建参数。

```bash
mvn clean package
docker build \
  --build-arg JAR_FILE=target/g2rain-gateway-webmvc-1.0.0.jar \
  --build-arg BUILD_VERSION=1.0.0 \
  --build-arg VCS_REF=$(git rev-parse HEAD) \
  -t ghcr.io/g2rain/g2rain-gateway-webmvc:1.0.0 .
docker run --rm -p 8083:8083 \
  -e NACOS_SERVER_ADDR=nacos.example.internal:8848 \
  -e SPRING_CLOUD_NACOS_DISCOVERY_USERNAME="$NACOS_DISCOVERY_USERNAME" \
  -e SPRING_CLOUD_NACOS_DISCOVERY_PASSWORD="$NACOS_DISCOVERY_PASSWORD" \
  -e SPRING_CLOUD_NACOS_CONFIG_USERNAME="$NACOS_CONFIG_USERNAME" \
  -e SPRING_CLOUD_NACOS_CONFIG_PASSWORD="$NACOS_CONFIG_PASSWORD" \
  ghcr.io/g2rain/g2rain-gateway-webmvc:1.0.0
```

最终镜像使用 `eclipse-temurin:25.0.4.1_1-jre`，包含一个 Spring Boot 可执行 Jar（其中打包了 `src/main/resources/application.yml`），工作目录为 `/app`，入口为 `java -jar /app/app.jar`，声明端口为 `8083`。OCI 元数据包括 source、Apache-2.0 license、title、version 和 revision。

`application.yml` 是已跟踪文件，且含 Nacos 开发默认用户名/密码；它会进入 Jar，继而进入镜像的 `/app/app.jar`。这不是由 `.dockerignore` 控制的敏感信息边界。维护者必须在共享/生产环境以环境变量、Nacos 配置中心或密钥管理服务覆盖这些值，并应在单独的安全整改中移除默认凭据；切勿把生产 Token、DPoP 私钥、API Key 或密码加入镜像或 Git。

## CI 构建、测试与 GHCR 推送

`.github/workflows/publish-ghcr.yml` 只在推送 `vMAJOR.MINOR.PATCH` Git tag 时触发；不会因分支推送、Pull Request 或手动触发而发布。它会严格校验 tag 格式、tag 提交已位于 `origin/main` 历史、同版本发布说明非空且存在、tag 与 Maven `project.version` 一致，然后执行 `mvn -B -ntp clean verify`。

之后工作流用 `GITHUB_TOKEN` 登录 GHCR，使用同一 Dockerfile 一次构建并推送 `ghcr.io/g2rain/g2rain-gateway-webmvc:1.0.0` 和 `ghcr.io/g2rain/g2rain-gateway-webmvc:sha-<完整提交 SHA>`。不推送 `latest`，不创建 GitHub Release，不部署服务。只有两个 `docker push` 都成功后才会在 Actions Summary 标记发布成功；任一失败都会使步骤失败并保留错误日志。

CI 的 `clean verify` 是 Maven 生命周期内的构建与测试，不是 Docker 运行、匿名拉取、在线演示或外部系统部署验证。当前本地 `verify` 已在 Enforcer 发现 `swagger-annotations-jakarta` 2.2.46/2.2.55 的上界冲突，历史记录另有 `g2rain-infra-api:1.0.0` 解析失败；在这些构建阻断解决前，CI 将在构建/测试阶段失败且不会推送镜像。

## GHCR 可见性、权限和拉取

首次成功推送后，维护者须在 GitHub Packages 的 **Package settings → Change visibility → Public** 将该包设为公开，匿名用户才能拉取：

```bash
docker pull ghcr.io/g2rain/g2rain-gateway-webmvc:1.0.0
```

若组织管理员禁止变更可见性，请联系组织管理员。若已有同名 GHCR 包报 `denied`，先在 **Package settings → Actions access** 授予 `g2rain/g2rain-gateway-webmvc` 写权限，并核对组织 Packages 策略；`packages: write` 是工作流所声明的令牌权限，不能单独保证已有包或组织策略允许推送。

## 发布顺序

1. 将版本、Dockerfile、工作流和 `docs/releases/v1.0.0.md` 先合入 `main`，并让 CI 前置检查通过。
2. 在该 `main` 提交创建并推送 `v1.0.0` tag。
3. 等待 GHCR 工作流完成；验证两个标签均出现后，再按需设置包为 Public。

本仓库只准备发布内容：不会由工作流创建 GitHub Release、修改包可见性或部署服务。
