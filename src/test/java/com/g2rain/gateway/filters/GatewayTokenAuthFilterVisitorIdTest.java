package com.g2rain.gateway.filters;

import com.g2rain.common.enums.SessionType;
import com.g2rain.common.web.TokenJWTPayload;
import com.g2rain.gateway.exception.GatewayException;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.token.TokenKeyManager;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * 验证网关载入并校验令牌中的访客 ID。
 *
 * @author alpha
 * @since 2026/11/26
 */
class GatewayTokenAuthFilterVisitorIdTest {

    private final GatewayTokenAuthFilter filter = new GatewayTokenAuthFilter(
        mock(WhiteListResolver.class),
        mock(TokenKeyManager.class)
    );

    @Test
    void buildPrincipalContextCopiesVisitorId() {
        TokenJWTPayload payload = new TokenJWTPayload();
        payload.setSessionType(SessionType.USER);
        payload.setVisitorId(701L);
        EdgePrincipalContext context = EdgePrincipalContext.of();

        ReflectionTestUtils.invokeMethod(filter, "buildPrincipalContext", context, payload);

        assertEquals(701L, context.getVisitorId());
    }

    @Test
    void anonymousTokenWithVisitorIdIsRejected() {
        TokenJWTPayload payload = new TokenJWTPayload();
        payload.setSessionType(SessionType.ANONYMOUS);
        payload.setOrganId(101L);
        payload.setUserId(201L);
        payload.setEntryId(301L);
        payload.setVisitorId(401L);

        assertThrows(
            GatewayException.class,
            () -> ReflectionTestUtils.invokeMethod(filter, "validateAnonymousSessionClaims", payload)
        );
    }
}
