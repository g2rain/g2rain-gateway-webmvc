package com.g2rain.gateway.filters;

import com.g2rain.common.enums.SessionType;
import com.g2rain.common.web.PrincipalHeaders;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PrincipalForwardFilter 主体头重建")
class PrincipalForwardFilterTest {

    @Test
    @DisplayName("过滤器顺序为 +800")
    void orderIsPlus800() {
        PrincipalForwardFilter filter = new PrincipalForwardFilter(mock(WhiteListResolver.class));
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 800, filter.getOrder());
    }

    @Test
    @DisplayName("伪造主体头被剥离后仅保留网关重建值")
    void forgedPrincipalHeadersReplacedByTrustedContext() throws Exception {
        WhiteListResolver whiteListResolver = mock(WhiteListResolver.class);
        when(whiteListResolver.shouldExclude(anyString(), any(ServerRequest.class))).thenReturn(false);
        PrincipalForwardFilter filter = new PrincipalForwardFilter(whiteListResolver);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/biz");
        servletRequest.addHeader(PrincipalHeaders.MEMBER_ID.getUpper(), "forged-member");
        servletRequest.addHeader(PrincipalHeaders.ORGAN_ID.getUpper(), "forged-organ");
        servletRequest.addHeader(PrincipalHeaders.USER_ID.getUpper(), "forged-user");
        ServerRequest request = ServerRequest.create(servletRequest, List.of());

        AtomicReference<ServerRequest> forwarded = new AtomicReference<>();
        HandlerFunction<ServerResponse> next = req -> {
            forwarded.set(req);
            return ServerResponse.ok().build();
        };

        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setSessionType(SessionType.MEMBER);
        context.setOrganId(10001L);
        context.setMemberId(9L);

        EdgePrincipalContextHolder.callWith(context, () -> {
            filter.filter(request, next);
            return null;
        });

        ServerRequest out = forwarded.get();
        assertEquals("9", out.headers().firstHeader(PrincipalHeaders.MEMBER_ID.getLower()));
        assertEquals("10001", out.headers().firstHeader(PrincipalHeaders.ORGAN_ID.getLower()));
        assertNull(out.headers().firstHeader(PrincipalHeaders.USER_ID.getLower()));
        assertEquals(1, out.headers().header(PrincipalHeaders.MEMBER_ID.getLower()).size());
        assertEquals(1, out.headers().header(PrincipalHeaders.ORGAN_ID.getLower()).size());
    }

    @Test
    @DisplayName("白名单请求也清除外部主体头且不注入上下文")
    void whitelistStillStripsExternalPrincipalHeaders() throws Exception {
        WhiteListResolver whiteListResolver = mock(WhiteListResolver.class);
        when(whiteListResolver.shouldExclude(eq("PrincipalForwardFilter"), any(ServerRequest.class))).thenReturn(true);
        PrincipalForwardFilter filter = new PrincipalForwardFilter(whiteListResolver);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/public");
        servletRequest.addHeader(PrincipalHeaders.MEMBER_ID.getUpper(), "forged-member");
        servletRequest.addHeader(PrincipalHeaders.ORGAN_ID.getUpper(), "forged-organ");
        ServerRequest request = ServerRequest.create(servletRequest, List.of());

        AtomicReference<ServerRequest> forwarded = new AtomicReference<>();
        HandlerFunction<ServerResponse> next = req -> {
            forwarded.set(req);
            return ServerResponse.ok().build();
        };

        filter.filter(request, next);

        ServerRequest out = forwarded.get();
        assertNull(out.headers().firstHeader(PrincipalHeaders.MEMBER_ID.getLower()));
        assertNull(out.headers().firstHeader(PrincipalHeaders.ORGAN_ID.getLower()));
        assertNull(out.headers().firstHeader(PrincipalHeaders.MEMBER_ID.getUpper()));
        assertNull(out.headers().firstHeader(PrincipalHeaders.ORGAN_ID.getUpper()));
    }
}
