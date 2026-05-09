package com.g2rain.gateway.filters;


import com.g2rain.basis.enums.AuthorizationStatus;
import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.cache.PassportPerm;
import com.g2rain.gateway.cache.UserPerm;
import com.g2rain.gateway.enums.GatewayErrorCode;
import com.g2rain.gateway.exception.GatewayException;
import com.g2rain.gateway.model.cache.BaseAuthority;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.utils.Constants;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
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
@Component
@AllArgsConstructor
public class ApiPermissionFilter implements HandlerFilterFunction<ServerResponse, ServerResponse>, Ordered {

    /**
     * 账号权限缓存
     */
    private final PassportPerm passportPerm;

    /**
     * 用户权限缓存
     */
    private final UserPerm userPerm;

    /**
     * {@code whiteListResolver} 用于判断当前请求是否命中白名单规则，
     * 如果命中则可以跳过当前 Filter 的执行。
     * <p>
     * 白名单规则包括全局规则和针对特定 Filter 的规则，匹配顺序为：
     * Filter 白名单 → 全局白名单，
     * 匹配方式包括 contextPath、exactPath、patternPath。
     * </p>
     */
    private final WhiteListResolver whiteListResolver;

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
        // 获取当前过滤器的类名（用于白名单判断）
        String filterName = this.getClass().getSimpleName();

        // 判断当前请求是否命中白名单规则
        // 命中则跳过本过滤器，直接进入下一个过滤器
        if (whiteListResolver.shouldExclude(filterName, request)) {
            return next.handle(request);
        }

        // 如果是账号, 进行账号接口鉴权
        EdgePrincipalContext context = EdgePrincipalContextHolder.get();
        Long applicationId = context.getApplicationId();

        Long apiId = (Long) request.attribute(Constants.ROUTE_INTERNAL_ID).orElse(null);
        if (Objects.isNull(apiId)) {
            throw new GatewayException(SystemErrorCode.UNAUTHORIZED, applicationId);
        }

        if (SessionType.isPassport(context.getSessionType())) {
            if (!passportPerm.hasApiPermission(apiId)) {
                throw new GatewayException(SystemErrorCode.UNAUTHORIZED, applicationId);
            }

            return next.handle(request);
        }

        BaseAuthority userApiPermission = userPerm.getApiPermission(
            context.getOrganId(), context.getUserId(), applicationId, apiId
        );

        // 没有接口权限能力
        if (Objects.isNull(userApiPermission)) {
            throw new GatewayException(SystemErrorCode.UNAUTHORIZED, applicationId);
        }

        // 订阅关停
        if (!Strings.equals(AuthorizationStatus.ACTIVATED.name(), userApiPermission.getStatus())) {
            throw new GatewayException(GatewayErrorCode.SUBSCRIPTION_EXPIRED);
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

