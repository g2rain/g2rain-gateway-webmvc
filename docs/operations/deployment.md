# 部署

```bash
mvn clean package
java -jar target/g2rain-gateway-webmvc-1.0.0.jar
```

Dockerfile 使用 Java 25 JRE Alpine，要求传入 `JAR_FILE`、`BUILD_VERSION`，声明端口 `8080`；应用默认端口为 `8083`，部署时需统一。当前依赖解析失败，且本次未验证 Jar、Jib 或 Docker 镜像。
