package com.g2rain.gateway.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


/**
 * Token 密钥配置属性类。
 * <p>
 * 用于通过 Nacos 或本地配置文件加载 Token 的公钥和私钥信息，
 * 支持多个密钥，可通过 {@code active} 字段标记当前激活的密钥。
 * 配置可动态刷新，结合 {@link RefreshScope} 使用。
 * </p>
 * <p>
 * 示例 YAML 配置：
 * <pre>{@code
 * token:
 *   keys:
 *     - key-id: yEMzeGLlhMpK5GxQKP5Fhg7JH9eALB7BK2BkadTOUxw
 *       algorithm: ES256
 *       active: true
 *       public-key: |
 *         -----BEGIN PUBLIC KEY-----
 *         MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEDwZbuQCoqp/oUrv4uWRgCW329J5A
 *         a5HpunjEjttgwHFZicDa6fUJNi7Djj8eZ8TdFotc0II0mLc1BVDdEkN8MA==
 *         -----END PUBLIC KEY-----
 *       private-key: |
 *         -----BEGIN PRIVATE KEY-----
 *         MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCC41ZiW3UJ946ZSuqy6
 *         WfOJB45cXeoji3tqcgAoZqki2Q==
 *         -----END PRIVATE KEY-----
 * }</pre>
 *
 * @author alpha
 * @since 2025/10/12
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "token")
public class TokenKeyProperties {

    /**
     * Token 密钥列表，可以配置多个密钥。
     * <p>
     * 每个 KeyConfig 对象表示一个密钥及其相关信息，包括 keyId、算法类型、公钥、私钥以及是否为当前激活密钥。
     * 系统在处理 Token 时只使用 {@code active=true} 的密钥。
     * </p>
     */
    private List<KeyConfig> keys = new ArrayList<>();

    /**
     * 单个 Token 密钥配置项。
     * <p>
     * 包含 keyId、算法类型、公钥、私钥，以及是否为当前激活密钥。
     * 该类可以直接映射 Nacos 或 YAML 配置中的每个密钥节点。
     * </p>
     */
    @Data
    public static class KeyConfig {

        /**
         * 密钥唯一标识，用于区分多个密钥。
         * <p>
         * 例如 "yEMzeGLlhMpK5GxQKP5Fhg7JH9eALB7BK2BkadTOUxw"。
         * </p>
         */
        private String keyId;

        /**
         * 公钥内容，可以直接写 PEM 格式字符串。
         * <p>
         * 用于 Token 验签，确保公私钥匹配。
         * </p>
         */
        private String publicKey;
    }
}
