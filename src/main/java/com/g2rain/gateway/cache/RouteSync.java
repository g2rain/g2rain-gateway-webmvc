package com.g2rain.gateway.cache;


import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.gateway.route.RouteCompiler;
import com.g2rain.infra.enums.InfraSyncerEnum;
import com.g2rain.infra.vo.RouteDefinitionVo;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author alpha
 * @since 2026/4/16
 */
@Slf4j
@Service
@AllArgsConstructor
public class RouteSync extends AbstractMessageStorage<Long, RouteDefinitionVo, String> {
    private final RouteCompiler routeCompiler;

    @Override
    protected @NonNull String dataSource() {
        return InfraSyncerEnum.ROUTE_DEFINE.name();
    }

    @Override
    protected @NonNull Class<RouteDefinitionVo> getValueType() {
        return RouteDefinitionVo.class;
    }

    @Override
    protected @NonNull Long getKey(@NonNull RouteDefinitionVo value) {
        return value.getId();
    }

    @Override
    protected void create(@NonNull Long key, RouteDefinitionVo value) {
        routeCompiler.upsert(value);
    }

    @Override
    protected void delete(@NonNull Long key) {
        routeCompiler.remove(key);
    }

    @Override
    protected void update(@NonNull Long key, RouteDefinitionVo value) {
        routeCompiler.upsert(value);
    }

    @Override
    protected String get(@NonNull Long key) {
        return null;
    }
}
