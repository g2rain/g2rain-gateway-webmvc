package com.g2rain.gateway.route;


import com.g2rain.basis.dto.ServiceRegistrySelectDto;
import com.g2rain.basis.vo.RouteDefinitionVo;
import com.g2rain.basis.vo.ServiceRegistryVo;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.client.RouteDefinitionClient;
import com.g2rain.gateway.client.ServiceRegistryClient;
import com.g2rain.gateway.matcher.MatcherUtils;
import com.g2rain.gateway.matcher.RuleDefinition;
import com.g2rain.gateway.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * 动态路由编译器（WebMVC）：控制面拉取、VO→{@link RouterFunction} 编译、运行期快照维护。
 *
 * <p>实现 {@link org.springframework.beans.factory.SmartInitializingSingleton}，在 Spring 单例全部实例化后
 * 执行 {@link #afterSingletonsInstantiated()}，从 {@link RouteDefinitionClient} 与 {@link ServiceRegistryClient}
 * 拉取数据，合并为 {@link Map}{@code <Long, RouteDefinitionVo>} 后调用 {@link #refresh(Map)} 一次性替换
 * {@link RouterFuncHolder} 中的 matcher 表与 {@code RouterFunction} 注册表。</p>
 *
 * <p>单条 {@link #upsert} / {@link #remove} 在同步方法内更新 {@link #routeDefinitions} 与
 * {@link RouterFuncHolder}，保证控制面增量与数据面读取一致。</p>
 *
 * @author alpha
 * @since 2026/2/23
 */
@Slf4j
@Component
public class GatewayRouteLoader implements SmartInitializingSingleton {
    /**
     * Gateway WebMVC 默认 HTTP 转发处理器
     */
    private static final HandlerFunction<ServerResponse> HTTP_HANDLER = HandlerFunctions.http();

    /**
     * 默认一级前缀裁剪器
     */
    private static final Function<ServerRequest, ServerRequest> STRIP_PREFIX = BeforeFilterFunctions.stripPrefix(1);

    /**
     * 当前生效的路由 VO 真相源：key 为路由 id，与 Basis 控制面主键一致。
     *
     * <p>全量刷新时 {@link #afterSingletonsInstantiated()} / {@link #refresh} 会整体写入；
     * 单条 {@link #upsert} / {@link #remove} 同步修改本表与 {@link RouterFuncHolder}。</p>
     */
    private final Map<Long, RouteDefinitionVo> routeDefinitions = new ConcurrentHashMap<>();

    /**
     * 路由运行期持有器
     */
    private final RouterFuncHolder holder;

    /**
     * 路由定义拉取客户端
     */
    private final RouteDefinitionClient routeDefinitionClient;

    /**
     * 服务注册数据源客户端
     */
    private final ServiceRegistryClient serviceRegistryClient;

    /**
     * 全局路由过滤器链
     *
     * <p>该过滤器链按 Spring 注入顺序组合, 在每条动态路由上统一挂载。</p>
     */
    private final HandlerFilterFunction<ServerResponse, ServerResponse> routeFilterChain;

    /**
     * 创建动态路由编译器
     *
     * @param holder                路由持有器
     * @param routeDefinitionClient 路由定义数据源客户端
     * @param serviceRegistryClient 服务注册数据源客户端
     * @param provider              全局路由过滤器提供器
     */
    public GatewayRouteLoader(RouterFuncHolder holder, RouteDefinitionClient routeDefinitionClient, ServiceRegistryClient serviceRegistryClient,
                              ObjectProvider<HandlerFilterFunction<ServerResponse, ServerResponse>> provider) {
        this.holder = holder;
        this.routeDefinitionClient = routeDefinitionClient;
        this.serviceRegistryClient = serviceRegistryClient;
        this.routeFilterChain = provider.orderedStream().reduce(HandlerFilterFunction::andThen).orElse(null);
    }

    /**
     * 容器启动完成后执行全量初始化：拉 API 路由 + 注册中心衍生路由，合并后刷新 {@link RouterFuncHolder}。
     *
     * <p>第一段：{@code routeDefinitionClient} 返回的列表转为 id→VO 的并发 Map（重复 id 时后者覆盖）。</p>
     * <p>第二段：服务注册列表转为「文档类 GET」占位 {@link RouteDefinitionVo}（路径 {@link Constants#DOC_PATH}），
     * 再并入同一 Map（id 冲突时同样以后写入为准）。</p>
     */
    @Override
    public void afterSingletonsInstantiated() {
        // 控制面：显式配置的 API 路由
        var result = routeDefinitionClient.selectRouteDefinitions();
        Map<Long, RouteDefinitionVo> newRoutes = Optional.ofNullable(result)
            .map(Result::getData)
            .stream()
            .flatMap(List::stream)
            .filter(v -> Objects.nonNull(v) && Objects.nonNull(v.getId()))
            .collect(Collectors.toConcurrentMap(
                RouteDefinitionVo::getId,
                Function.identity(),
                (_, b) -> b
            ));

        // 注册中心：为每个服务衍生一条便于 Swagger/文档 访问的占位路由
        var srvRegistry = serviceRegistryClient.selectList(new ServiceRegistrySelectDto());
        newRoutes.putAll(Optional.ofNullable(srvRegistry)
            .map(Result::getData)
            .stream()
            .flatMap(List::stream)
            .filter(v -> Objects.nonNull(v) && Objects.nonNull(v.getId()))
            .map(this::registry2route)
            .collect(Collectors.toConcurrentMap(
                RouteDefinitionVo::getId,
                Function.identity(),
                (_, b) -> b
            )));

        refresh(newRoutes);
    }

    /**
     * 从控制面拉取全量路由并替换当前运行期快照。
     *
     * @param newRoutes 合并后的路由 VO 映射；为空则直接返回且不修改 holder
     */
    public synchronized void refresh(Map<Long, RouteDefinitionVo> newRoutes) {
        if (Collections.isEmpty(newRoutes)) {
            return;
        }

        this.routeDefinitions.putAll(newRoutes);
        holder.replace(this.routeDefinitions.values().stream()
            .map(this::toDefinition)
            .flatMap(Optional::stream)
            .toList());
        log.info("路由刷新成功, 当前活跃路由数: {}", holder.size());
    }

    /**
     * 新增或更新单条路由
     *
     * @param vo 路由定义传输对象
     */
    public synchronized void upsert(RouteDefinitionVo vo) {
        if (Objects.isNull(vo) || Objects.isNull(vo.getId())) {
            log.warn("忽略无效路由 upsert 请求: {}", vo);
            return;
        }

        toDefinition(vo).ifPresentOrElse(
            definition -> {
                holder.upsert(definition);
                this.routeDefinitions.put(vo.getId(), vo);
            },
            () -> {
                holder.remove(vo.getId());
                this.routeDefinitions.remove(vo.getId());
            }
        );

        log.info("单条路由更新完成, routeId={}, 当前活跃路由数={}",
            vo.getId(), holder.size()
        );
    }

    /**
     * 删除单条路由
     *
     * @param routeId 路由标识
     */
    public synchronized void remove(Long routeId) {
        if (Objects.isNull(routeId)) {
            return;
        }

        holder.remove(routeId);
        this.routeDefinitions.remove(routeId);
        log.info("单条路由删除完成, routeId={}, 当前活跃路由数={}",
            routeId, holder.size()
        );
    }

    /**
     * 将传输对象转换为 matcher 可识别的规则定义
     *
     * @param vo 路由定义传输对象
     * @return 运行期规则定义
     */
    private Optional<RuleDefinition<RouterFunction<ServerResponse>>> toDefinition(RouteDefinitionVo vo) {
        try {
            RouterFunction<ServerResponse> func = toRouterFunction(vo);
            if (Objects.isNull(func)) {
                return Optional.empty();
            }

            String normalizePath = MatcherUtils.normalize("/" + vo.getRoutePrefix() + "/" + vo.getPath());
            return Optional.of(new RuleDefinition<>(vo.getId(), vo.getMethod(), normalizePath, func));
        } catch (Exception e) {
            log.error("路由编译失败 ID {}: {}", vo.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 将路由定义编译为 Gateway WebMVC 原生 {@link RouterFunction}
     *
     * @param vo 路由定义传输对象
     * @return 编译后的路由函数
     */
    private RouterFunction<ServerResponse> toRouterFunction(RouteDefinitionVo vo) {
        try {
            String normalizePath = MatcherUtils.normalize("/" + vo.getRoutePrefix() + "/" + vo.getPath());
            var pathPredicate = GatewayRequestPredicates.path(normalizePath);
            var predicate = Optional.ofNullable(vo.getMethod())
                .filter(Strings::isNotBlank)
                .map(String::toUpperCase)
                .map(HttpMethod::valueOf)
                .map(RequestPredicates::method)
                .map(pathPredicate::and)
                .orElse(pathPredicate);

            String context = vo.getRoutePrefix();
            URI targetUri = URI.create(vo.getEndpoint());

            /*
             * 将路由上下文写入 servlet request, 供后续过滤器或业务链路读取。
             */
            UnaryOperator<ServerRequest> contextEnhancer = request -> {
                request.servletRequest().setAttribute(
                    Constants.ROUTE_CONTEXT_PATH, context
                );

                return request;
            };

            /*
             * 路由构建顺序固定为：
             * 1. 谓词匹配
             * 2. 注入上下文
             * 3. 设置目标 URI
             * 4. 裁剪一级路径前缀
             * 5. 执行 HTTP 转发
             */
            var routeBuilder = GatewayRouterFunctions.route(String.valueOf(vo.getId()))
                .route(predicate, HTTP_HANDLER)
                .before(contextEnhancer)
                .before(BeforeFilterFunctions.uri(targetUri))
                .before(STRIP_PREFIX);

            /*
             * 若存在全局过滤器链, 则将其统一挂到当前动态路由上。
             */
            Optional.ofNullable(routeFilterChain).ifPresent(routeBuilder::filter);

            return routeBuilder.build();
        } catch (Exception e) {
            log.error("路由编译失败, 跳过该条配置 [ID: {}, Path: {}]: {}",
                vo.getId(), vo.getPath(), e.getMessage()
            );
            return null;
        }
    }

    /**
     * 服务注册对象转路由定义实体对象
     * 如果后续存在多个特例的路由规则, 在考虑重构
     * 是否将 path 存入 nacos 等因素再进行考量
     *
     * @param registry 服务注册对象
     * @return 路由定义实体对象
     */
    public RouteDefinitionVo registry2route(ServiceRegistryVo registry) {
        RouteDefinitionVo route = new RouteDefinitionVo();
        route.setId(registry.getId());
        route.setMethod(HttpMethod.GET.name());
        route.setPath(Constants.DOC_PATH);
        route.setEndpoint(registry.getEndpoint());
        route.setRoutePrefix(registry.getRoutePrefix());
        return route;
    }
}
