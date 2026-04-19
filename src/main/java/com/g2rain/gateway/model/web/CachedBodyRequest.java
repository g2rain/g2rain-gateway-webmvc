package com.g2rain.gateway.model.web;


import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.enums.GatewayErrorCode;
import com.g2rain.gateway.exception.GatewayException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * {@code CachedBodyRequest} 是 {@link HttpServletRequestWrapper} 的扩展实现,
 * 用于缓存 HTTP 请求的请求体（body）和请求头（headers）, 以便重复读取
 * <p>
 * 原生的 {@link HttpServletRequest} 请求体只能读取一次, 本类通过缓存字节数组和包装流的方式实现多次读取
 * 另外, 提供对请求头的可修改操作, 可新增或删除请求头
 * </p>
 * <p>
 * 适用于需要在过滤器或拦截器中重复读取请求体的场景, 例如日志记录, 签名验证或安全检查
 * </p>
 *
 * @author alpha
 * @since 2026/2/21
 */
public class CachedBodyRequest extends HttpServletRequestWrapper {

    /**
     * 缓存的请求头集合, key 为经 {@link Locale#ROOT} 小写规范化后的头名, value 为头值列表
     */
    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    /**
     * 缓存请求体的字节数组
     */
    private final byte[] body;

    /**
     * 构造方法：包装原始 {@link HttpServletRequest} 并缓存请求体和请求头
     *
     * @param delegate 原始请求对象
     * @param maxBytes 请求体最大可缓存字节数, 超过将抛出异常
     * @throws IOException 当读取请求体发生 IO 错误时抛出
     */
    public CachedBodyRequest(HttpServletRequest delegate, long maxBytes) throws IOException {
        super(delegate);

        // 缓存请求体
        this.body = readRequestBodyWithLimit(delegate, maxBytes);

        // 缓存请求头；key 与 Servlet 一致（头名大小写不敏感），统一小写存储
        Collections.list(delegate.getHeaderNames()).forEach(name -> headers.put(
            name.toLowerCase(Locale.ROOT), new ArrayList<>(Collections.list(delegate.getHeaders(name)))
        ));
    }

    /**
     * 获取缓存的 {@link ServletInputStream}, 用于重复读取请求体
     *
     * @return 缓存的请求体输入流
     */
    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream cachedBuffer = new ByteArrayInputStream(this.body);
        return new ServletInputStream() {
            /**
             * 从缓存流中读取单个字节
             */
            @Override
            public int read() {
                return cachedBuffer.read();
            }

            @Override
            public int read(@Nonnull byte[] b, int off, int len) {
                return cachedBuffer.read(b, off, len);
            }

            /**
             * 判断请求体是否已读完
             */
            @Override
            public boolean isFinished() {
                return cachedBuffer.available() == 0;
            }

            /**
             * 总是返回 true, 表示流随时可读
             */
            @Override
            public boolean isReady() {
                return true;
            }

            /**
             * 异步读取监听器暂不支持
             */
            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * 获取请求体的 {@link BufferedReader} 以字符方式读取
     * 使用请求指定的编码或 UTF-8 作为默认编码
     *
     * @return 请求体的 {@link BufferedReader}
     */
    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(
            getInputStream(), StandardCharsets.UTF_8
        ));
    }

    /**
     * 获取指定名称的第一个请求头值
     *
     * @param name 请求头名称
     * @return 第一个头值, 若不存在则返回 {@code null}
     */
    @Override
    public String getHeader(String name) {
        if (Objects.isNull(name)) {
            return null;
        }

        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        if (Objects.isNull(values) || values.isEmpty()) {
            return null;
        }

        return values.getFirst();
    }

    /**
     * 获取指定名称的所有请求头值
     *
     * @param name 请求头名称
     * @return {@link Enumeration} 的头值列表, 若不存在则返回空枚举
     */
    @Override
    public Enumeration<String> getHeaders(String name) {
        if (Objects.isNull(name)) {
            return Collections.emptyEnumeration();
        }

        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        if (Objects.isNull(values) || values.isEmpty()) {
            return Collections.emptyEnumeration();
        }

        return Collections.enumeration(values);
    }

    /**
     * 获取所有请求头名称
     *
     * @return {@link Enumeration} 的请求头名称列表
     */
    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headers.keySet());
    }

    /**
     * 新增请求头, 如果该头已存在则追加值
     *
     * @param name  请求头名称
     * @param value 请求头值
     */
    public void addHeader(String name, String value) {
        if (Strings.isBlank(name)) {
            return;
        }

        name = name.toLowerCase(Locale.ROOT);
        headers.computeIfAbsent(name, _ -> new ArrayList<>()).add(value);
    }

    /**
     * 删除指定名称的请求头
     *
     * @param names 要删除的头名称列表
     */
    public void removeHeader(List<String> names) {
        if (Objects.isNull(names) || names.isEmpty()) {
            return;
        }

        names.stream().filter(Strings::isNotBlank).map(o ->
            o.toLowerCase(Locale.ROOT)
        ).forEach(headers::remove);
    }

    /**
     * 获取缓存的请求体字节数组
     *
     * @return 请求体字节数组
     */
    public byte[] asBytes() {
        return this.body;
    }

    /**
     * 读取请求体并限制最大字节数, 超过则抛出 {@link GatewayException}
     *
     * @param request  原始请求对象
     * @param maxBytes 最大可读取字节数
     * @return 请求体字节数组
     * @throws IOException 当读取请求体发生 IO 错误
     */
    private byte[] readRequestBodyWithLimit(HttpServletRequest request, long maxBytes) throws IOException {
        try (ServletInputStream input = request.getInputStream(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            // 缓冲区大小
            byte[] tmp = new byte[4096];
            int read;
            long total = 0;

            while ((read = input.read(tmp)) != -1) {
                total += read;

                // 超过限制抛出异常
                if (total > maxBytes) {
                    throw new GatewayException(GatewayErrorCode.REQUEST_BODY_TOO_LARGE, maxBytes);
                }

                buffer.write(tmp, 0, read);
            }

            return buffer.toByteArray();
        }
    }
}
