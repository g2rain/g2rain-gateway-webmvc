package com.g2rain.gateway.exception;


import com.g2rain.common.exception.ErrorMessageRegistry;
import com.g2rain.common.exception.LocalizedErrorMessage;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.client.ErrorMessageClient;
import com.g2rain.infra.dto.I18nMessageSelectDto;
import com.g2rain.infra.enums.InfraSyncerEnum;
import com.g2rain.infra.vo.I18nMessageVo;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>{@code ErrorMessageStorage} 是 {@link ErrorMessageRegistry} 的默认实现类，
 * 用于管理和缓存系统的本地化错误消息。</p>
 * <p>
 * 该类使用内存缓存 {@link #MESSAGE_CACHE} 存储错误码与对应本地化消息的映射，支持按完整 locale 或语言前缀进行查找。
 * </p>
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * ErrorMessageStorage registry = new ErrorMessageStorage();
 * registry.load();
 * String msg = registry.getMessage("ERR001", "zh_CN");
 * System.out.println(msg);
 * }</pre>
 *
 * @author alpha
 * @since 2025/10/6
 */
@RequiredArgsConstructor
public class ErrorMessageStorage extends ErrorMessageRegistry {

    private final ErrorMessageClient errorMessageClient;

    /**
     * 错误码与本地化消息的缓存映射
     */
    private static final Map<String, String> MESSAGE_CACHE = new ConcurrentHashMap<>();

    /**
     * 加载所有错误消息，默认实现为空（NOP）。
     */
    @Override
    public void load() {
        I18nMessageSelectDto selectDto = new I18nMessageSelectDto();
        Result<List<I18nMessageVo>> result = errorMessageClient.selectList(selectDto);
        if (Objects.isNull(result) || !result.isSuccess()) {
            return;
        }

        List<I18nMessageVo> messages = result.getData();
        if (Collections.isEmpty(messages)) {
            return;
        }

        for (I18nMessageVo message : messages) {
            StringBuilder key = new StringBuilder(message.getMessageCode());
            key.append("_").append(message.getLanguageCode());
            String region = message.getRegionCode();
            if (Strings.isNotBlank(region)) {
                key.append("_").append(region);
            }

            MESSAGE_CACHE.put(key.toString(), message.getMessageText());
        }
    }

    /**
     * 根据错误码和 locale 获取对应的错误消息。
     * 如果 locale 对应的完整消息不存在，则尝试使用 locale 的语言前缀查找。
     * 搞的过于复杂的原因是Locale可能保持变体zh_CN_#Hans
     *
     * @param errorCode 错误码
     * @param locale    本地化标识，例如 "zh_CN", 如果为 {@code null} 则使用 {@link Locale#getDefault()}
     * @return 对应的错误消息，找不到返回空字符串
     */
    @Override
    public String getMessage(String errorCode, String locale) {
        if (Strings.isBlank(errorCode)) {
            return null;
        }

        if (Strings.isBlank(locale)) {
            locale = Locale.getDefault().toString();
        }

        // 只分成最多 3 部分，避免变体干扰
        String[] parts = locale.split("[_-]", 3);
        String language = parts[0];
        String country = null;
        if (parts.length > 1) {
            country = parts[1];
        }

        // 构建 baseKey
        String baseKey = errorCode + "_" + language;
        // 优先查找完整 locale (language + "_" + country)
        if (Strings.isNotBlank(country)) {
            String fullKey = baseKey + "_" + country;
            String msg = MESSAGE_CACHE.get(fullKey);
            if (Strings.isNotBlank(msg)) {
                return msg;
            }
        }

        // 尝试仅语言查找
        return MESSAGE_CACHE.get(baseKey);
    }

    @Override
    protected @NonNull String dataSource() {
        return InfraSyncerEnum.ERROR_MSG.name();
    }

    @Override
    protected @NonNull Class<LocalizedErrorMessage> getValueType() {
        return LocalizedErrorMessage.class;
    }

    @Override
    protected @NonNull String getKey(@NonNull LocalizedErrorMessage value) {
        return value.getErrorCode() + "_" + value.getLocale();
    }

    @Override
    protected void create(@NonNull String key, LocalizedErrorMessage value) {
        MESSAGE_CACHE.put(key, value.getMessageTemplate());
    }

    @Override
    protected void delete(@NonNull String key) {
        MESSAGE_CACHE.remove(key);
    }

    @Override
    protected void update(@NonNull String key, LocalizedErrorMessage value) {
        MESSAGE_CACHE.put(key, value.getMessageTemplate());
    }

    @Override
    protected String get(@NonNull String key) {
        return MESSAGE_CACHE.get(key);
    }
}
