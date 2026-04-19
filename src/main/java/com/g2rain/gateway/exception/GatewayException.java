package com.g2rain.gateway.exception;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ErrorCode;
import com.g2rain.common.exception.FieldError;

import java.util.List;
import java.util.Map;

/**
 * 网关业务异常类。
 * <p>
 * 继承自 {@link BusinessException}，用于在网关层封装统一的异常信息。
 * 支持根据错误码、字段错误、参数占位符以及底层异常进行构造。
 * </p>
 *
 * @author alpha
 * @since 2025/10/13
 */
public class GatewayException extends BusinessException {

    /**
     * 使用错误码构造异常。
     *
     * @param errorCode 错误码
     */
    public GatewayException(ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * 使用错误码与命名参数构造异常。
     *
     * @param errorCode 错误码
     * @param keyArgs   命名参数，用于消息模板替换
     */
    public GatewayException(ErrorCode errorCode, Map<String, Object> keyArgs) {
        super(errorCode, keyArgs);
    }

    /**
     * 使用错误码与索引参数构造异常。
     *
     * @param errorCode 错误码
     * @param indexArgs 索引参数，用于消息模板替换
     */
    public GatewayException(ErrorCode errorCode, Object... indexArgs) {
        super(errorCode, indexArgs);
    }

    /**
     * 使用错误码与字段错误构造异常。
     *
     * @param errorCode   错误码
     * @param fieldErrors 字段错误列表
     */
    public GatewayException(ErrorCode errorCode, List<FieldError> fieldErrors) {
        super(errorCode, fieldErrors);
    }

    /**
     * 使用错误码与异常原因构造异常。
     *
     * @param errorCode 错误码
     * @param cause     异常原因
     */
    public GatewayException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    /**
     * 完整构造函数，支持错误码、命名参数、索引参数、字段错误及异常原因。
     *
     * @param errorCode   错误码
     * @param keyArgs     命名参数
     * @param indexArgs   索引参数
     * @param fieldErrors 字段错误列表
     * @param cause       异常原因
     */
    public GatewayException(ErrorCode errorCode, Map<String, Object> keyArgs, Object[] indexArgs, List<FieldError> fieldErrors, Throwable cause) {
        super(errorCode, keyArgs, indexArgs, fieldErrors, cause);
    }
}
