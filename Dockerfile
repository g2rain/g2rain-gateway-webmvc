FROM eclipse-temurin:25.0.4.1_1-jre

ARG JAR_FILE=target/g2rain-gateway-webmvc-1.0.0.jar
ARG BUILD_VERSION
ARG VCS_REF

LABEL org.opencontainers.image.source="https://github.com/g2rain/g2rain-gateway-webmvc" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.title="g2rain-gateway-webmvc" \
      org.opencontainers.image.version="${BUILD_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}"

ENV APP_VERSION=${BUILD_VERSION}
WORKDIR /app
COPY ${JAR_FILE} /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
