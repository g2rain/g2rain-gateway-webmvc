package com.g2rain.gateway.filters;


import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.gateway.exception.GatewayException;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.permission.ApiPermissionDecision;
import com.g2rain.gateway.permission.ApiPermissionService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Objects;

/**
 * 接口权限校验过滤器（骨架）。
 *
 * <p>
 * 用于在网关侧根据请求 {@code URI + Method} 判断当前登录用户/客户端是否有权限访问该接口。
 * 具体的权限数据来源与匹配规则由你自行实现（例如查缓存/远端校验/本地规则表等）。
 * </p>
 */
@Slf4j
// @Component
@AllArgsConstructor
public class ApiPermissionFilter implements HandlerFilterFunction<ServerResponse, ServerResponse>, Ordered {

    private final ApiPermissionService apiPermissionService;

    /**
     * 接口权限校验入口。
     *
     * <p>
     * 从上下文提取应用身份后，按请求方法与路径调用权限服务判定；
     * 未授权时抛出 {@link GatewayException}，授权通过则继续执行后续链路。
     * </p>
     *
     * @param request 当前请求
     * @param next    下游处理器
     * @return 下游响应
     * @throws Exception 权限校验失败或下游处理异常
     */
    @Override
    public ServerResponse filter(@NonNull ServerRequest request, @NonNull HandlerFunction<ServerResponse> next) throws Exception {
        EdgePrincipalContext context = EdgePrincipalContextHolder.get();
        String applicationCode = Objects.nonNull(context) ? context.getApplicationCode() : null;
        boolean backEndRequest = Objects.nonNull(context) && context.isBackEnd();

        ApiPermissionDecision decision = apiPermissionService.check(
            request.method(),
            request.path(),
            applicationCode,
            backEndRequest
        );

        if (!decision.allowed()) {
            throw new GatewayException(
                SystemErrorCode.UNAUTHORIZED,
                Objects.toString(decision.interfaceCode(), request.path())
            );
        }
        return next.handle(request);
    }

    /**
     * 定义过滤器执行顺序。
     *
     * @return 执行优先级
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 600;
    }
}

