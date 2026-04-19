package com.g2rain.gateway.cache;


import com.g2rain.common.syncer.AbstractMessageStorage;
import lombok.NonNull;

/**
 * @author alpha
 * @since 2026/4/17
 */
public class Permission extends AbstractMessageStorage<Long, String, String> {
    @Override
    protected @NonNull String dataSource() {
        return "";
    }

    @Override
    protected @NonNull Class<String> getValueType() {
        return String.class;
    }

    @Override
    protected @org.jspecify.annotations.NonNull Long getKey(@org.jspecify.annotations.NonNull String value) {
        return 0L;
    }

    @Override
    protected void create(@org.jspecify.annotations.NonNull Long key, String value) {

    }

    @Override
    protected void delete(@org.jspecify.annotations.NonNull Long key) {

    }

    @Override
    protected void update(@org.jspecify.annotations.NonNull Long key, String value) {

    }

    @Override
    protected String get(@org.jspecify.annotations.NonNull Long key) {
        return "";
    }
}
