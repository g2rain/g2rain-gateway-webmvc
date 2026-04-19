package com.g2rain.gateway.model.web;


import com.g2rain.common.utils.Constants;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.http.MediaType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code CachedBodyResponse} 是 {@link HttpServletResponseWrapper} 的扩展实现,
 * 用于缓存 HTTP 响应体, 以便在提交前重复读取或修改响应内容
 * <p>
 * 原生的 {@link HttpServletResponse} 响应体只能写入一次, 本类通过缓存字节数组和包装流实现多次操作
 * 可在过滤器或拦截器中对响应进行日志记录, JSON 修改或其他处理
 * </p>
 *
 * @author alpha
 * @since 2026/2/21
 */
public class CachedBodyResponse extends HttpServletResponseWrapper {
    /**
     * 缓存响应体的字节数组输出流, 默认容量 1024
     */
    private final ByteArrayOutputStream body = new ByteArrayOutputStream(1024);

    /**
     * 缓存 {@link ServletOutputStream} 实例
     */
    private ServletOutputStream outputStream;

    /**
     * 缓存 {@link PrintWriter} 实例
     */
    private PrintWriter writer;

    /**
     * 标识是否跳过缓存逻辑
     */
    private volatile Boolean skipCaching;

    /**
     * 构造方法, 包装原始 {@link HttpServletResponse}
     *
     * @param delegate 原始响应对象
     */
    public CachedBodyResponse(HttpServletResponse delegate) {
        super(delegate);
    }

    /**
     * 获取响应体的 {@link ServletOutputStream} , 可缓存写入的字节
     *
     * @return 响应体输出流
     * @throws IOException IO 异常
     */
    @Override
    public synchronized ServletOutputStream getOutputStream() throws IOException {
        // 跳过缓存逻辑, 直接返回底层流
        if (shouldSkipCaching()) {
            return super.getOutputStream();
        }

        if (Objects.nonNull(this.writer)) {
            throw new IllegalStateException("getWriter() already called");
        }

        if (Objects.isNull(this.outputStream)) {
            this.outputStream = new ServletOutputStream() {
                @Override
                public void write(int b) {
                    body.write(b);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        return this.outputStream;
    }

    /**
     * 获取响应体的 {@link PrintWriter} , 可缓存写入的字符
     *
     * @return 响应体字符输出流
     * @throws IOException IO 异常
     */
    @Override
    public synchronized PrintWriter getWriter() throws IOException {
        // 跳过缓存逻辑, 直接返回底层字符流
        if (shouldSkipCaching()) {
            return super.getWriter();
        }

        if (Objects.nonNull(this.outputStream)) {
            throw new IllegalStateException("getOutputStream() already called");
        }

        if (Objects.isNull(this.writer)) {
            // 将缓存流包装为字符流, 自动 flush
            this.writer = new PrintWriter(new OutputStreamWriter(
                this.body, StandardCharsets.UTF_8
            ), true);
        }

        return this.writer;
    }

    /**
     * 获取缓存的响应体字节数组
     *
     * @return 响应体字节数组
     */
    public byte[] getBody() {
        // 确保 writer 内容刷新到缓存流
        if (Objects.nonNull(this.writer)) {
            this.writer.flush();
        }

        // 返回缓存响应体字节数组
        return this.body.toByteArray();
    }

    /**
     * 刷新缓存的响应体, 可替换内容
     *
     * @param body 新的响应体字节数组
     */
    public void refresh(byte[] body) {
        // 跳过缓存逻辑, 不做刷新
        if (shouldSkipCaching()) {
            return;
        }

        // 响应已提交, 无法修改
        if (super.isCommitted()) {
            return;
        }

        // body 为 null 时替换为空字节数组
        if (Objects.isNull(body)) {
            body = Constants.EMPTY_BYTE;
        }

        // 确保 writer 内容刷新到缓存
        if (Objects.nonNull(this.writer)) {
            this.writer.flush();
        }

        // 清空缓存流
        this.body.reset();

        // 写入新的响应体内容
        this.body.write(body, 0, body.length);

        // 更新响应内容长度
        super.setContentLength(this.body.size());
    }

    /**
     * 将缓存的响应体写入底层输出流并刷新缓冲
     *
     * @throws IOException IO 异常
     */
    public void flush() throws IOException {
        // 跳过缓存逻辑, 不刷新
        if (shouldSkipCaching()) {
            return;
        }

        // 响应已提交, 无法刷新
        if (super.isCommitted()) {
            return;
        }

        // 将缓存的响应体写入底层输出流
        super.getOutputStream().write(getBody());
        // 刷新缓冲
        super.flushBuffer();
    }

    /**
     * 判断是否跳过缓存逻辑
     *
     * @return true 表示跳过缓存, false 表示缓存响应体
     */
    private boolean shouldSkipCaching() {
        // 已计算过, 直接返回
        if (Objects.nonNull(this.skipCaching)) {
            return this.skipCaching;
        }

        // 只有 JSON 响应才缓存, 其他类型跳过
        this.skipCaching = Optional.ofNullable(getContentType())
            .map(MediaType::parseMediaType)
            .filter(MediaType.APPLICATION_JSON::isCompatibleWith)
            .isEmpty();

        return this.skipCaching;
    }
}
