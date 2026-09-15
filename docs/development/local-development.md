# 本地开发

要求 JDK 25、Maven 3.9+、可解析的 `g2rain-basis-api:1.0.0` 与 `g2rain-infra-api:1.0.0`，以及 Nacos、Basis、Infra 和 Redis。

```bash
mvn test
mvn spring-boot:run
```

默认服务名 `g2rain-gateway`、端口 `8083`、Profile `dev`、虚拟线程开启。当前测试会在依赖解析阶段失败，需先发布或安装缺失的 Infra API 依赖。
