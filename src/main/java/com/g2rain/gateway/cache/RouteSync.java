package com.g2rain.gateway.cache;


import com.g2rain.basis.enums.BasisSyncerEnum;
import com.g2rain.basis.vo.RouteDefinitionVo;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.gateway.route.GatewayRouteLoader;
import jakarta.annotation.PreDestroy;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 路由消息同步存储
 *
 * <p>该实现保留消息模型的单条语义，但在消费侧对 create 事件做短窗口聚合，
 * 以识别批量导入场景。update 与 delete 仍然保持单条即时生效</p>
 *
 * @author alpha
 * @since 2026/4/16
 */
@Slf4j
@Service
public class RouteSync extends AbstractMessageStorage<Long, RouteDefinitionVo, String> {
    /**
     * create 事件聚合窗口时长，单位毫秒
     */
    private static final long CREATE_WINDOW_MILLIS = 1_000L;

    /**
     * 路由控制面编译器
     */
    private final GatewayRouteLoader gatewayRouteLoader;

    /**
     * create 缓冲区批次切换锁
     */
    private final Object createBufferMonitor = new Object();

    /**
     * 待批量提交的 create 缓冲区
     *
     * <p>同一主键重复写入时以后到值覆盖先到值。</p>
     */
    private final Map<Long, RouteDefinitionVo> pendingCreates = new ConcurrentHashMap<>();

    /**
     * 标记当前是否已存在待执行的 create flush 任务
     */
    private final AtomicBoolean createFlushScheduled = new AtomicBoolean(false);

    /**
     * create 聚合窗口调度器
     *
     * <p>调度线程仅负责触发 flush，真正 flush 逻辑运行在虚拟线程中。</p>
     */
    private final ScheduledExecutorService createWindowScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "route-sync-create-window");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 创建路由同步存储
     *
     * @param gatewayRouteLoader 路由编译器
     */
    public RouteSync(GatewayRouteLoader gatewayRouteLoader) {
        this.gatewayRouteLoader = gatewayRouteLoader;
    }

    /**
     * 返回当前消息源名称
     *
     * @return 消息源名称
     */
    @Override
    protected @NonNull String dataSource() {
        return BasisSyncerEnum.API_ROUTE.name();
    }

    /**
     * 返回消息 value 类型
     *
     * @return value 类型
     */
    @Override
    protected @NonNull Class<RouteDefinitionVo> getValueType() {
        return RouteDefinitionVo.class;
    }

    /**
     * 提取路由主键
     *
     * @param value 路由定义
     * @return 路由主键
     */
    @Override
    protected @NonNull Long getKey(@NonNull RouteDefinitionVo value) {
        return value.getId();
    }

    /**
     * 处理 create 事件
     *
     * <p>create 会进入短窗口聚合，窗口到期后按批次数量分流：
     * 单条走 upsert，多条走全量 refresh。</p>
     *
     * @param key   路由主键
     * @param value 路由定义
     */
    @Override
    protected void create(@NonNull Long key, RouteDefinitionVo value) {
        if (Objects.isNull(value)) {
            return;
        }

        synchronized (createBufferMonitor) {
            pendingCreates.put(key, value);
        }

        scheduleCreateFlush();
    }

    /**
     * 处理 delete 事件
     *
     * <p>delete 需要保持顺序语义，因此会先冲刷待提交的 create，再立即删除目标路由</p>
     *
     * @param key 路由主键
     */
    @Override
    protected void delete(@NonNull Long key) {
        flushPendingCreates();
        gatewayRouteLoader.remove(key);
    }

    /**
     * 处理 update 事件
     *
     * <p>update 保持单条即时生效语义，因此不会进入窗口聚合</p>
     *
     * @param key   路由主键
     * @param value 路由定义
     */
    @Override
    protected void update(@NonNull Long key, RouteDefinitionVo value) {
        flushPendingCreates();
        gatewayRouteLoader.upsert(value);
    }

    /**
     * 当前实现不提供按主键回查
     *
     * @param key 路由主键
     * @return 固定返回 {@code null}
     */
    @Override
    protected String get(@NonNull Long key) {
        return null;
    }

    /**
     * 关闭调度器前尽力冲刷待提交 create
     */
    @PreDestroy
    public void destroy() {
        createWindowScheduler.shutdown();
    }

    /**
     * 为 create 事件安排一次窗口 flush
     */
    private void scheduleCreateFlush() {
        if (!createFlushScheduled.compareAndSet(false, true)) {
            return;
        }

        createWindowScheduler.schedule(
            () -> Thread.ofVirtual().name("route-sync-create-flush").start(this::flushPendingCreatesSafely),
            CREATE_WINDOW_MILLIS,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * 安全执行 create 缓冲区 flush
     */
    private void flushPendingCreatesSafely() {
        try {
            flushPendingCreates();
        } catch (Exception e) {
            log.error("批量创建路由 flush 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 冲刷当前待提交的 create 缓冲区
     *
     * <p>若在 flush 过程中又有新的 create 到达，会重新安排下一轮窗口任务</p>
     */
    private void flushPendingCreates() {
        try {
            List<RouteDefinitionVo> batch = drainPendingCreates();
            if (batch.isEmpty()) {
                return;
            }

            if (batch.size() == 1) {
                gatewayRouteLoader.upsert(batch.getFirst());
                log.info("创建路由窗口 flush 完成, mode=upsert, batchSize=1");
            } else {
                ConcurrentMap<Long, RouteDefinitionVo> newRoutes = batch.stream()
                    .filter(v -> Objects.nonNull(v) && Objects.nonNull(v.getId()))
                    .collect(Collectors.toConcurrentMap(
                        RouteDefinitionVo::getId,
                        Function.identity(),
                        (_, b) -> b
                    ));

                gatewayRouteLoader.refresh(newRoutes);
                log.info("创建路由窗口 flush 完成, mode=refresh, batchSize={}", batch.size());
            }
        } finally {
            // 保证刷新动作执行完之前, 不会有新的 schedule 任务被创建
            createFlushScheduled.set(false);

            // 兜底检查: 防止在执行上面业务逻辑时刚好进来了新数据却没被 schedule
            synchronized (createBufferMonitor) {
                if (!pendingCreates.isEmpty()) {
                    scheduleCreateFlush();
                }
            }
        }
    }

    /**
     * 提取并清空当前 create 缓冲区
     *
     * @return 当前批次的 create 数据
     */
    private List<RouteDefinitionVo> drainPendingCreates() {
        synchronized (createBufferMonitor) {
            if (pendingCreates.isEmpty()) {
                return List.of();
            }

            List<RouteDefinitionVo> batch = new ArrayList<>(pendingCreates.values());
            pendingCreates.clear();
            return batch;
        }
    }
}
