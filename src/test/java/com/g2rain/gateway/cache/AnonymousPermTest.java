package com.g2rain.gateway.cache;

import com.g2rain.basis.vo.SessionApiPermissionVo;
import com.g2rain.common.enums.SessionType;
import com.g2rain.common.model.Result;
import com.g2rain.gateway.client.AuthorityClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnonymousPermTest {

    private static final Long ORGAN_ID = 20001L;

    private AuthorityClient authorityClient;
    private AnonymousPerm anonymousPerm;

    @BeforeEach
    void setUp() {
        authorityClient = mock(AuthorityClient.class);
        anonymousPerm = new AnonymousPerm(authorityClient);
        anonymousPerm.delete(ORGAN_ID);
    }

    @Test
    void hasApiPermission_requiresActiveEntryAndOpenedApi() {
        when(authorityClient.getSessionApiPermissions(SessionType.ANONYMOUS.name(), ORGAN_ID))
            .thenReturn(Result.success(snapshot(List.of(42L), List.of(5L))));

        assertTrue(anonymousPerm.hasApiPermission(ORGAN_ID, 5L, 42L));
        assertFalse(anonymousPerm.hasApiPermission(ORGAN_ID, 6L, 42L));
        assertFalse(anonymousPerm.hasApiPermission(ORGAN_ID, 5L, 99L));
        assertFalse(anonymousPerm.hasApiPermission(ORGAN_ID, null, 42L));
        verify(authorityClient, times(1)).getSessionApiPermissions(SessionType.ANONYMOUS.name(), ORGAN_ID);
    }

    @Test
    void delete_shouldReloadSnapshotSoDisabledEntryIsRejected() {
        when(authorityClient.getSessionApiPermissions(SessionType.ANONYMOUS.name(), ORGAN_ID))
            .thenReturn(Result.success(snapshot(List.of(42L), List.of(5L))))
            .thenReturn(Result.success(snapshot(List.of(42L), List.of())));

        assertTrue(anonymousPerm.hasApiPermission(ORGAN_ID, 5L, 42L));

        // 模拟收到 ANONYMOUS_PERM：接入码 5 被停用
        anonymousPerm.delete(ORGAN_ID);

        assertFalse(anonymousPerm.hasApiPermission(ORGAN_ID, 5L, 42L));
        verify(authorityClient, times(2)).getSessionApiPermissions(SessionType.ANONYMOUS.name(), ORGAN_ID);
    }

    private static SessionApiPermissionVo snapshot(List<Long> apiIds, List<Long> entryIds) {
        SessionApiPermissionVo vo = new SessionApiPermissionVo();
        vo.setOrganId(ORGAN_ID);
        vo.setVersion(1L);
        vo.setApiIds(apiIds);
        vo.setEntryIds(entryIds);
        return vo;
    }
}
