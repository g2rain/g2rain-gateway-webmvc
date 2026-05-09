package com.g2rain.gateway.cache;


import com.g2rain.basis.enums.BasisSyncerEnum;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.gateway.client.AuthorityClient;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author alpha
 * @since 2026/5/5
 */
@Slf4j
@Service
@AllArgsConstructor
public class PassportPerm extends AbstractMessageStorage<Long, Long, String> {
    private final AuthorityClient authorityClient;

    /**
     * 账号的接口能力缓存
     */
    private static final Set<Long> PASSPORT_API_PERMISSIONS = new HashSet<>();

    @Override
    public void load() {
        // 查询账号的能力, 只需要缓存一份, 因为所有账号都有相同的能力
        var passportApiPermissions = authorityClient.getPassportApiPermissions();
        if (Objects.isNull(passportApiPermissions) || !passportApiPermissions.isSuccess()) {
            return;
        }

        PASSPORT_API_PERMISSIONS.addAll(passportApiPermissions.getData());
    }

    @Override
    protected @NonNull String dataSource() {
        return BasisSyncerEnum.PASSPORT_PERM.name();
    }

    @Override
    protected @NonNull Class<Long> getValueType() {
        return Long.class;
    }

    @Override
    protected @NonNull Long getKey(@NonNull Long value) {
        return 0L;
    }

    @Override
    protected void create(@NonNull Long key, Long value) {
        PASSPORT_API_PERMISSIONS.add(value);
    }

    @Override
    protected void delete(@NonNull Long key) {
        PASSPORT_API_PERMISSIONS.remove(key);
    }

    @Override
    protected void update(@NonNull Long key, Long value) {
        PASSPORT_API_PERMISSIONS.add(value);
    }

    @Override
    protected String get(@NonNull Long key) {
        return "";
    }

    public boolean hasApiPermission(Long apiId) {
        return PASSPORT_API_PERMISSIONS.contains(apiId);
    }
}
