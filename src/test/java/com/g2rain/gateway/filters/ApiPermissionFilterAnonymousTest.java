package com.g2rain.gateway.filters;

import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.gateway.cache.AnonymousPerm;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ApiPermissionFilter ANONYMOUS 会话")
class ApiPermissionFilterAnonymousTest {

    private UserPerm userPerm;
    private AnonymousPerm anonymousPerm;
    private ApiPermissionFilter filter;
    private HandlerFunction<ServerResponse> next;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        userPerm = mock(UserPerm.class);
        anonymousPerm = mock(AnonymousPerm.class);
        WhiteListResolver whiteListResolver = mock(WhiteListResolver.class);
        when(whiteListResolver.shouldExclude(anyString(), any(ServerRequest.class))).thenReturn(false);
        filter = new ApiPermissionFilter(mock(DefaultPerm.class), userPerm, mock(MemberPerm.class), anonymousPerm,
            new MemberPermissionProperties(), whiteListResolver);
        next = mock(HandlerFunction.class);
        when(next.handle(any())).thenReturn(ServerResponse.ok().build());
    }

    @Test
    @DisplayName("接入码启用且接口已开通时放行，不走员工角色链")
    void allowedWhenEntryActiveAndApiOpened() throws Exception {
        when(anonymousPerm.hasApiPermission(10001L, 5L, 42L)).thenReturn(true);

        run();

        verify(next).handle(any());
        verify(userPerm, never()).getApiPermission(anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("接入码已停用或接口未开通时拒绝")
    void rejectedWhenNotAllowed() throws Exception {
        when(anonymousPerm.hasApiPermission(10001L, 5L, 42L)).thenReturn(false);

        GatewayException ex = assertThrows(GatewayException.class, this::run);
        assertEquals(SystemErrorCode.UNAUTHORIZED.code(), ex.getErrorCode());
        verify(next, never()).handle(any());
    }

    @Test
    @DisplayName("权限快照加载失败时 503，失败即关闭")
    void unavailableWhenSnapshotLoadFails() throws Exception {
        when(anonymousPerm.hasApiPermission(10001L, 5L, 42L)).thenThrow(new IllegalStateException("basis down"));

        GatewayException ex = assertThrows(GatewayException.class, this::run);
        assertEquals(GatewayErrorCode.ANONYMOUS_PERM_UNAVAILABLE.code(), ex.getErrorCode());
        verify(next, never()).handle(any());
    }

    private void run() throws Exception {
        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setSessionType(SessionType.ANONYMOUS);
        context.setOrganId(10001L);
        context.setUserId(77L);
        context.setEntryId(5L);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/harness/v1/chat/completions");
        servletRequest.setAttribute(Constants.ROUTE_INTERNAL_ID, 42L);
        ServerRequest request = ServerRequest.create(servletRequest, List.of());

        EdgePrincipalContextHolder.callWith(context, () -> {
            filter.filter(request, next);
            return null;
        });
    }
}
