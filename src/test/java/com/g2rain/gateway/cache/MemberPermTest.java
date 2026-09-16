package com.g2rain.gateway.cache;

import com.g2rain.basis.vo.SessionApiPermissionVo;
import com.g2rain.common.enums.SessionType;
import com.g2rain.common.model.Result;
import com.g2rain.gateway.client.AuthorityClient;
import com.g2rain.gateway.model.cache.MemberOrganPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberPermTest {

    private AuthorityClient authorityClient;
    private MemberPerm memberPerm;

    @BeforeEach
    void setUp() {
        authorityClient = mock(AuthorityClient.class);
        memberPerm = new MemberPerm(authorityClient);
    }

    @Test
    void getOrLoad_shouldCacheAndInvalidateByOrgan() {
        SessionApiPermissionVo vo = new SessionApiPermissionVo();
        vo.setOrganId(10001L);
        vo.setVersion(3L);
        vo.setApiIds(List.of(42L, 43L));
        when(authorityClient.getSessionApiPermissions(SessionType.MEMBER.name(), 10001L))
            .thenReturn(Result.success(vo));

        MemberOrganPermission first = memberPerm.getOrLoad(10001L);
        MemberOrganPermission second = memberPerm.getOrLoad(10001L);
        assertTrue(first.apiIds().contains(42L));
        assertTrue(second.apiIds().contains(42L));
        verify(authorityClient, times(1))
            .getSessionApiPermissions(SessionType.MEMBER.name(), 10001L);

        assertTrue(memberPerm.hasApiPermission(10001L, 42L));
        assertFalse(memberPerm.hasApiPermission(10001L, 99L));

        memberPerm.delete(10001L);
        when(authorityClient.getSessionApiPermissions(SessionType.MEMBER.name(), 10001L))
            .thenReturn(Result.success(vo));
        memberPerm.getOrLoad(10001L);
        verify(authorityClient, times(2))
            .getSessionApiPermissions(SessionType.MEMBER.name(), 10001L);
    }
}
