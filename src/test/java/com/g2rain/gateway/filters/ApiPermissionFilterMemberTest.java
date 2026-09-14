package com.g2rain.gateway.filters;

import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.gateway.cache.DefaultPerm;
import com.g2rain.gateway.cache.MemberPerm;
import com.g2rain.gateway.cache.UserPerm;
import com.g2rain.gateway.config.MemberPermissionProperties;
import com.g2rain.gateway.enums.GatewayErrorCode;
import com.g2rain.gateway.exception.GatewayException;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.utils.Constants;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ApiPermissionFilter MEMBER 会话")
class ApiPermissionFilterMemberTest {

    private ApiPermissionFilter newFilter(DefaultPerm defaultPerm, UserPerm userPerm, MemberPerm memberPerm,
                                          MemberPermissionProperties props) {
        WhiteListResolver whiteListResolver = mock(WhiteListResolver.class);
        when(whiteListResolver.shouldExclude(anyString(), any(ServerRequest.class))).thenReturn(false);
        return new ApiPermissionFilter(defaultPerm, userPerm, memberPerm, props, whiteListResolver);
    }

    private MemberPermissionProperties enforceProps() {
        MemberPermissionProperties props = new MemberPermissionProperties();
        props.setMode("enforce");
        return props;
    }

    private ServerRequest requestWithApiId(Long apiId) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/biz");
        servletRequest.setAttribute(Constants.ROUTE_INTERNAL_ID, apiId);
        return ServerRequest.create(servletRequest, java.util.List.of());
    }

    @Test
    @DisplayName("MEMBER 命中 MemberPerm 时放行")
    void memberAllowedWhenMemberPermHits() throws Exception {
        DefaultPerm defaultPerm = mock(DefaultPerm.class);
        UserPerm userPerm = mock(UserPerm.class);
        MemberPerm memberPerm = mock(MemberPerm.class);
        when(memberPerm.hasApiPermission(10001L, 42L)).thenReturn(true);

        ApiPermissionFilter filter = newFilter(defaultPerm, userPerm, memberPerm, enforceProps());
        HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);
        when(next.handle(any())).thenReturn(ServerResponse.ok().build());

        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setSessionType(SessionType.MEMBER);
        context.setOrganId(10001L);
        context.setMemberId(9L);

        EdgePrincipalContextHolder.callWith(context, () -> {
            filter.filter(requestWithApiId(42L), next);
            return null;
        });

        verify(next).handle(any());
        verify(defaultPerm, never()).hasApiPermission(anyLong());
        verify(userPerm, never()).getApiPermission(anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("MEMBER 未开通时拒绝")
    void memberRejectedWhenNotOpened() {
        DefaultPerm defaultPerm = mock(DefaultPerm.class);
        UserPerm userPerm = mock(UserPerm.class);
        MemberPerm memberPerm = mock(MemberPerm.class);
        when(memberPerm.hasApiPermission(10001L, 42L)).thenReturn(false);

        ApiPermissionFilter filter = newFilter(defaultPerm, userPerm, memberPerm, enforceProps());
        HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);

        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setSessionType(SessionType.MEMBER);
        context.setOrganId(10001L);
        context.setMemberId(9L);

        GatewayException ex = assertThrows(GatewayException.class, () ->
            EdgePrincipalContextHolder.callWith(context, () -> {
                filter.filter(requestWithApiId(42L), next);
                return null;
            })
        );
        assertEquals(SystemErrorCode.UNAUTHORIZED.code(), ex.getErrorCode());
    }

    @Test
    @DisplayName("MEMBER 混入 passportId 时拒绝")
    void memberRejectedWithPassportId() {
        DefaultPerm defaultPerm = mock(DefaultPerm.class);
        UserPerm userPerm = mock(UserPerm.class);
        MemberPerm memberPerm = mock(MemberPerm.class);

        ApiPermissionFilter filter = newFilter(defaultPerm, userPerm, memberPerm, enforceProps());
        HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);

        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setSessionType(SessionType.MEMBER);
        context.setOrganId(10001L);
        context.setMemberId(9L);
        context.setPassportId(8L);

        GatewayException ex = assertThrows(GatewayException.class, () ->
            EdgePrincipalContextHolder.callWith(context, () -> {
                filter.filter(requestWithApiId(42L), next);
                return null;
            })
        );
        assertEquals(SystemErrorCode.UNAUTHENTICATED.code(), ex.getErrorCode());
        verify(memberPerm, never()).hasApiPermission(anyLong(), anyLong());
    }

    @Test
    @DisplayName("MEMBER 回源失败时失败关闭")
    void memberFailsClosedOnLoadError() {
        DefaultPerm defaultPerm = mock(DefaultPerm.class);
        UserPerm userPerm = mock(UserPerm.class);
        MemberPerm memberPerm = mock(MemberPerm.class);
        when(memberPerm.hasApiPermission(eq(10001L), eq(42L))).thenThrow(new RuntimeException("basis down"));

        ApiPermissionFilter filter = newFilter(defaultPerm, userPerm, memberPerm, enforceProps());
        HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);

        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setSessionType(SessionType.MEMBER);
        context.setOrganId(10001L);
        context.setMemberId(9L);

        GatewayException ex = assertThrows(GatewayException.class, () ->
            EdgePrincipalContextHolder.callWith(context, () -> {
                filter.filter(requestWithApiId(42L), next);
                return null;
            })
        );
        assertEquals(GatewayErrorCode.MEMBER_PERM_UNAVAILABLE.code(), ex.getErrorCode());
    }
}
