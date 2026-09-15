package com.g2rain.gateway.filters;


import com.g2rain.basis.enums.AuthorizationStatus;
import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.cache.DefaultPerm;
import com.g2rain.gateway.cache.MemberPerm;
import com.g2rain.gateway.cache.UserPerm;
import com.g2rain.gateway.config.MemberPermissionProperties;
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
 * 按路由 ID 校验 Passport / MEMBER / User 对接口的访问权限。
 */
@Slf4j
@Component
@AllArgsConstructor
public class ApiPermissionFilter implements HandlerFilterFunction<ServerResponse, ServerResponse>, Ordered {

    private final DefaultPerm defaultPerm;

    private final UserPerm userPerm;

    private final MemberPerm memberPerm;

    private final MemberPermissionProperties memberPermissionProperties;

    private final WhiteListResolver whiteListResolver;

    @Override
    public ServerResponse filter(@NonNull ServerRequest request, @NonNull HandlerFunction<ServerResponse> next) throws Exception {
        String filterName = this.getClass().getSimpleName();

        if (whiteListResolver.shouldExclude(filterName, request)) {
            return next.handle(request);
        }

        EdgePrincipalContext context = EdgePrincipalContextHolder.get();
        Long applicationId = context.getApplicationId();

        Long apiId = (Long) request.attribute(Constants.ROUTE_INTERNAL_ID).orElse(null);
        if (Objects.isNull(apiId)) {
            throw new GatewayException(SystemErrorCode.UNAUTHORIZED, applicationId);
        }

        if (SessionType.isPassport(context.getSessionType())) {
            if (defaultPerm.hasApiPermission(apiId)) {
                return next.handle(request);
            }

            throw new GatewayException(SystemErrorCode.UNAUTHORIZED, applicationId);
        }

        if (SessionType.isMember(context.getSessionType())) {
            return authorizeMember(request, next, context, apiId, applicationId);
        }

        BaseAuthority userApiPermission = userPerm.getApiPermission(
            context.getOrganId(), context.getUserId(), context.getRoleIds(), applicationId, apiId
        );

        if (Objects.isNull(userApiPermission)) {
            throw new GatewayException(SystemErrorCode.UNAUTHORIZED, applicationId);
        }

        if (!Strings.equals(AuthorizationStatus.ACTIVATED.name(), userApiPermission.getStatus())) {
            throw new GatewayException(GatewayErrorCode.SUBSCRIPTION_EXPIRED);
        }

        return next.handle(request);
    }

    private ServerResponse authorizeMember(ServerRequest request, HandlerFunction<ServerResponse> next,
                                           EdgePrincipalContext context, Long apiId, Long applicationId)
        throws Exception {
        if (!isValidMemberPrincipal(context)) {
            throw new GatewayException(SystemErrorCode.UNAUTHENTICATED, "MEMBER");
        }

        Long organId = context.getOrganId();
        if (memberPermissionProperties.isShadow()) {
            boolean defaultOk = defaultPerm.hasApiPermission(apiId);
            boolean memberOk = false;
            try {
                memberOk = memberPerm.hasApiPermission(organId, apiId);
            } catch (Exception err) {
                log.warn("MEMBER shadow MemberPerm load failed organId={} apiId={}", organId, apiId, err);
            }
            if (defaultOk != memberOk) {
                log.warn("MEMBER permission shadow mismatch organId={} apiId={} defaultPerm={} memberPerm={}",
                    organId, apiId, defaultOk, memberOk);
            }
            if (defaultOk) {
                return next.handle(request);
            }
            throw new GatewayException(SystemErrorCode.UNAUTHORIZED, applicationId);
        }

        try {
            if (memberPerm.hasApiPermission(organId, apiId)) {
                return next.handle(request);
            }
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.MEMBER_PERM_UNAVAILABLE, e);
        }
        throw new GatewayException(SystemErrorCode.UNAUTHORIZED, applicationId);
    }

    private static boolean isValidMemberPrincipal(EdgePrincipalContext context) {
        Long organId = context.getOrganId();
        Long memberId = context.getMemberId();
        return Objects.nonNull(organId) && organId > 0
            && Objects.nonNull(memberId) && memberId > 0
            && Objects.isNull(context.getUserId())
            && Objects.isNull(context.getPassportId());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 600;
    }
}
