package com.g2rain.gateway.token;


import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.gateway.config.TokenKeyProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * TokenKeyManager 类负责加载和管理 EC 公钥。
 * <p>
 * 该类会在 Spring 容器初始化时从 {@link TokenKeyProperties} 中加载 PEM 格式的公钥，
 * 并将其存入内存中以便快速访问。提供以下功能：
 * <ul>
 *     <li>根据 keyId 获取对应公钥</li>
 *     <li>监听 Nacos 配置变更，仅在指定 DataId {@value #DATA_ID} 变更时刷新公钥</li>
 * </ul>
 * </p>
 * <p>
 * 刷新策略为原子替换整个 Map，保证线程安全，避免刷新期间获取到不完整的密钥数据。
 * </p>
 * <p>
 * 注意：该 Bean 标注了 {@link RefreshScope}，但实际刷新仅依赖 Nacos 指定配置监听器。
 * </p>
 *
 * @author alpha
 * @since 2025/10/12
 */
@Slf4j
@Component
@RefreshScope
public class TokenKeyManager {

    /**
     * 存储 EC 公钥的映射表，键为密钥 ID，值为对应的 {@link ECPublicKey}。
     */
    private volatile Map<String, ECPublicKey> keys = new HashMap<>();

    /**
     * 注入的 TokenKeyProperties，包含公钥配置列表。
     */
    private final TokenKeyProperties properties;

    /**
     * Nacos 配置管理器，用于注册配置监听器。
     */
    private final NacosConfigManager nacosConfigManager;

    private static final String DATA_ID = "g2rain-token-keypair.yml";
    private static final String GROUP = "g2rain";

    /**
     * 构造函数，注入 TokenKeyProperties 和 NacosConfigManager。
     *
     * @param properties         公钥配置属性
     * @param nacosConfigManager Nacos 配置管理器
     */
    public TokenKeyManager(TokenKeyProperties properties, NacosConfigManager nacosConfigManager) {
        this.properties = properties;
        this.nacosConfigManager = nacosConfigManager;
    }

    /**
     * 初始化方法，在 Spring 完成 Bean 注入后执行。
     * <p>
     * 加载所有公钥并注册 Nacos 配置监听器。当指定 DataId 配置变更时，调用 {@link #reloadKeys()} 重新加载公钥。
     * </p>
     *
     * @throws BusinessException 如果初始化或监听器注册失败
     */
    @PostConstruct
    public void init() {
        // 初始加载
        reloadKeys();

        try {
            // 注册 Nacos 配置变更监听器，只监听 DATA_ID
            ConfigService configService = nacosConfigManager.getConfigService();
            configService.addListener(DATA_ID, GROUP, new Listener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("TokenKey配置变更，重新加载密钥...");
                    reloadKeys();
                }

                @Override
                public Executor getExecutor() {
                    return null; // 使用默认线程池
                }
            });
        } catch (NacosException e) {
            // 如果密钥加载失败，则抛出业务异常，提示初始化 PEM 错误
            throw new BusinessException(SystemErrorCode.SYSTEM_INTERNAL_ERROR, null, new String[]{"初始化Token公私钥"}, null, e);
        }
    }

    /**
     * 重新加载公钥配置，构建新的 Map 并原子替换，保证刷新期间线程安全。
     *
     * @throws BusinessException 如果 PEM 公钥解析失败
     */
    private synchronized void reloadKeys() {
        try {
            Map<String, ECPublicKey> newKeys = new HashMap<>();
            for (TokenKeyProperties.KeyConfig config : properties.getKeys()) {
                // 加载公钥
                ECPublicKey pk = loadPublicKey(config.getPublicKey());

                // 将加载的公钥存入 Map 中，key 为密钥的 ID
                newKeys.put(config.getKeyId(), pk);
            }

            // 原子替换
            this.keys = newKeys;
        } catch (Exception e) {
            // 如果密钥加载失败，则抛出业务异常，提示初始化 PEM 错误
            throw new BusinessException(SystemErrorCode.SYSTEM_INTERNAL_ERROR, "初始化PEM错误");
        }
    }

    /**
     * 根据密钥 ID 获取对应的 EC 公钥。
     *
     * @param keyId 密钥 ID
     * @return {@link ECPublicKey} 对象，如果未找到返回 null
     */
    public ECPublicKey getKey(String keyId) {
        return keys.get(keyId);
    }

    /**
     * 将 PEM 格式的公钥字符串解析为 {@link ECPublicKey}。
     * <p>
     * 解析步骤：
     * <ol>
     *     <li>去掉头尾标识及空白字符</li>
     *     <li>Base64 解码</li>
     *     <li>使用 {@link X509EncodedKeySpec} 和 {@link KeyFactory} 构建 {@link ECPublicKey}</li>
     * </ol>
     * </p>
     *
     * @param publicKey PEM 格式公钥字符串
     * @return {@link ECPublicKey} 对象
     * @throws NoSuchAlgorithmException 如果不支持 "EC" 算法
     * @throws InvalidKeySpecException  如果密钥格式不正确
     */
    private ECPublicKey loadPublicKey(String publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // 去掉公钥字符串中的头尾部分，并去除空格和换行符
        String pem = publicKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            // 去掉所有空格和换行
            .replace(" ", "");
        // 对 PEM 字符串进行 Base64 解码
        byte[] bytes = Base64.getDecoder().decode(pem);
        // 通过 X509EncodedKeySpec 创建公钥对象
        X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(spec);
    }
}
