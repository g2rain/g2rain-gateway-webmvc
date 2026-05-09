package com.g2rain.gateway.cache;


import com.g2rain.basis.enums.BasisSyncerEnum;
import com.g2rain.basis.vo.ServiceRegistryVo;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.gateway.route.GatewayRouteLoader;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author alpha
 * @since 2026/4/17
 */
@Slf4j
@Service
public class InternalRoute extends AbstractMessageStorage<Long, ServiceRegistryVo, String> {
    /**
     * 路由控制面编译器
     */
    private final GatewayRouteLoader gatewayRouteLoader;

    /**
     * 创建路由同步存储
     *
     * @param gatewayRouteLoader 路由编译器
     */
    public InternalRoute(GatewayRouteLoader gatewayRouteLoader) {
        this.gatewayRouteLoader = gatewayRouteLoader;
    }

    @Override
    protected @NonNull String dataSource() {
        return BasisSyncerEnum.INTERNAL_ROUTE.name();
    }

    @Override
    protected @NonNull Class<ServiceRegistryVo> getValueType() {
        return ServiceRegistryVo.class;
    }

    @Override
    protected @NonNull Long getKey(@NonNull ServiceRegistryVo value) {
        return value.getId();
    }

    @Override
    protected void create(@NonNull Long key, ServiceRegistryVo value) {
        gatewayRouteLoader.upsert(gatewayRouteLoader.registry2route(value));
    }

    @Override
    protected void delete(@NonNull Long key) {
        gatewayRouteLoader.remove(key);
    }

    @Override
    protected void update(@NonNull Long key, ServiceRegistryVo value) {
        create(key, value);
    }

    @Override
    protected String get(@NonNull Long key) {
        return null;
    }
}
