package com.g2rain.gateway.components;

import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecBuilder;
import com.g2rain.gateway.model.event.GatewayEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 网关侧 Kafka 日志发送（可选）。
 * <p>
 * {@code spring.kafka.enabled=false} 时 {@link #send} 直接返回，不访问 Kafka。
 * 未配置 {@code spring.kafka.bootstrap-servers} 时通常不会创建 {@link KafkaTemplate}，本类通过
 * {@link ObjectProvider} 判空，同样不发送、启动不依赖集群。
 * </p>
 *
 * @author alpha
 * @since 2026/4/13
 */
@Slf4j
@Component
public class KafkaLogSender {

    /**
     * 是否真正发 Kafka（仅控制本类逻辑；不等于 Spring Boot 自带开关）。
     */
    @Value("${spring.kafka.enabled:false}")
    private boolean kafkaEnabled;

    /**
     * JSON 序列化（与请求线程无关，静态即可）。
     */
    private static final JsonCodec JSON = JsonCodecBuilder.builder().withDefaults().build();

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    public KafkaLogSender(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider) {
        this.kafkaTemplateProvider = kafkaTemplateProvider;
    }

    /**
     * 发送一条日志消息到指定 topic。
     * <p>关闭或未装配 {@link KafkaTemplate} 时立即返回，调用方无需判空。</p>
     *
     * @param topic        目标 topic
     * @param gatewayEvent 载荷
     */
    public void send(String topic, GatewayEvent gatewayEvent) {
        if (!kafkaEnabled) {
            return;
        }

        KafkaTemplate<String, String> template = kafkaTemplateProvider.getIfAvailable();
        if (Objects.isNull(template)) {
            return;
        }

        try {
            template.send(topic, JSON.obj2str(gatewayEvent));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
