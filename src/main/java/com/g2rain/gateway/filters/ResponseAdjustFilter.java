package com.g2rain.gateway.filters;


import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecBuilder;
import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.json.SuccessIgnoreFieldMixIn;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.cache.AppName;
import com.g2rain.gateway.cache.OrganName;
import com.g2rain.gateway.model.web.CachedBodyResponse;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 响应体后处理器：对成功响应的业务数据做字段补全。
 * <p>
 * 由 {@link CachedBodyFilter} 在响应写回前统一调度，本处理器仅在白名单未命中且响应体可解析为
 * {@link Result} 时生效。
 * </p>
 * <ul>
 *     <li>读取缓存响应体并解析为 {@code Result<JsonNode>}</li>
 *     <li>仅处理 {@code result.isSuccess() == true} 的响应</li>
 *     <li>按映射规则批量把 {@code xxxId -> xxxName} 补全到 {@code data}</li>
 *     <li>将改写后的响应重新序列化回写</li>
 * </ul>
 *
 * <h2>字段映射规则</h2>
 * <pre>
 * "companyOrganId" → "companyOrganName"
 * "tenantId"        → "tenantName"
 * "organId"         → "organName"
 * "applicationId"   → "applicationName"
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Bean
 * public ResponseAdjustFilter responseAdjustFilter() {
 *     return new ResponseAdjustFilter();
 * }
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *     <li>需要配合 {@link CachedBodyFilter} 使用，确保响应体可重复读取</li>
 *     <li>非成功响应保持原样返回，不在此处抛异常</li>
 *     <li>字段映射可根据业务扩展修改 {@link #fieldMappings}</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/6
 */
@Component
public class ResponseAdjustFilter implements ResponseBodyProcessor {

    /**
     * JSON 序列化器
     */
    private final JsonCodec jsonSerializer = JsonCodecBuilder.builder().withDefaults().withConfig(jsonMapper ->
        jsonMapper.addMixIn(Result.class, SuccessIgnoreFieldMixIn.class)
    ).build();

    /**
     * JSON 反序列化器
     */
    private final JsonCodec jsonDeserializer = JsonCodecFactory.instance();

    /**
     * {@code whiteListResolver} 用于判断当前请求是否命中白名单规则，
     * 如果命中则可以跳过当前 Filter 的执行。
     * <p>
     * 白名单规则包括全局规则和针对特定 Filter 的规则，匹配顺序为：
     * Filter 白名单 → 全局白名单，
     * 匹配方式包括 contextPath、exactPath、patternPath。
     * </p>
     */
    private final WhiteListResolver whiteListResolver;

    /**
     * 字段映射规则
     */
    private final List<FieldMapping> fieldMappings;

    public ResponseAdjustFilter(WhiteListResolver whiteListResolver, AppName appName, OrganName organName) {
        this.whiteListResolver = whiteListResolver;
        this.fieldMappings = List.of(
            new FieldMapping("tenantId", "tenantName", organName::getNames),
            new FieldMapping("organId", "organName", organName::getNames),
            new FieldMapping("applicationId", "applicationName", appName::getNames)
        );
    }

    @Override
    public byte[] process(HttpServletRequest request, CachedBodyResponse response, byte[] body) {
        // 如果命中白名单，则跳过当前 Processor
        if (whiteListResolver.shouldExclude(processorName(), request)) {
            return body;
        }

        // 没有缓存响应体(比如非json格式[文件下载]不会缓存), 不再改写
        if (Collections.isEmpty(body)) {
            return body;
        }

        // 1. 将缓存 body 直接解析为 Result<JsonNode>
        Result<JsonNode> result = jsonDeserializer.byte2obj(body, new TypeReference<>() {
        });

        // 如果没有值, 不再改写
        if (Objects.isNull(result)) {
            return body;
        }

        // 如果默认值, 说明不是Result包装, 直接跳过处理
        if (result.getStatus() == 0) {
            return body;
        }

        // 2. 业务状态码非成功：保持下游已写入的响应体
        if (!result.isSuccess()) {
            return body;
        }

        // 3. 状态码是 200 → 解析 data 部分
        JsonNode data = result.getData();
        if (Objects.isNull(data)) {
            return jsonSerializer.obj2byte(result);
        }

        // 4. 替换原始响应体
        adjustData(data);
        // 5. 将调整后的 Result 转回缓存 body
        return jsonSerializer.obj2byte(result);
    }

    /**
     * 调整响应 {@code data} 节点（仅做“结构拆分 + 目标节点收集”）。
     * <p>
     * 网关返回体的 {@code data} 可能是：
     * </p>
     * <ul>
     *     <li>数组：{@code [ {...}, {...} ]}</li>
     *     <li>对象：{@code { ... }}</li>
     *     <li>分页对象：{@code { records: [ {...}, ... ], pageNum: 1, ... }}</li>
     * </ul>
     *
     * <p>
     * 本方法只负责把所有需要调整的 {@link ObjectNode} 收集到 {@code targets}，最后交给批量处理方法
     * {@link #doAdjustData(List)}。这样后续把“批量收集 id → 批量查询 → 批量回写”的逻辑放到批量方法中，
     * 就能避免在网关高并发下对每条记录进行单独 IO 查询。
     * </p>
     *
     * @param data JSON data 节点
     */
    private void adjustData(@NonNull JsonNode data) {
        // 统一收集需要处理的 ObjectNode（只收集对象节点；数组里若存在非对象元素会被忽略）
        List<ObjectNode> targets = new ArrayList<>();

        // 情况 1：data 本身是数组：把数组里的对象节点全部加入 targets
        if (data.isArray()) {
            addAllObjectNodes(data, targets);
            doAdjustData(targets);
            return;
        }

        // 情况 2：非对象：不处理
        if (!data.isObject()) {
            return;
        }

        // 情况 3：对象但不是分页结构：把自身作为目标节点
        if (!data.has("records") || !data.has("pageNum")) {
            addIfObjectNode(data, targets);
            doAdjustData(targets);
            return;
        }

        // 情况 4：分页结构：只处理 records 数组中的对象节点
        JsonNode records = data.get("records");
        if (Objects.isNull(records) || !records.isArray()) {
            return;
        }

        addAllObjectNodes(records, targets);
        doAdjustData(targets);
    }

    /**
     * 将一个数组节点中的所有对象元素（{@link ObjectNode}）追加到 targets。
     * <p>
     * 这是一个“小工具方法”：让调用方不必重复写遍历与 instanceof 判断。
     * </p>
     *
     * @param arrayNode 可能为数组的节点（非数组或为 null 会被忽略）
     * @param targets   目标收集容器
     */
    private void addAllObjectNodes(JsonNode arrayNode, List<ObjectNode> targets) {
        // 调用方保证传入的是数组节点
        for (JsonNode item : arrayNode) {
            addIfObjectNode(item, targets);
        }
    }

    /**
     * 如果 node 是 {@link ObjectNode}，则加入 targets；否则忽略。
     *
     * @param node    待判断节点
     * @param targets 目标收集容器
     */
    private void addIfObjectNode(JsonNode node, List<ObjectNode> targets) {
        if (node instanceof ObjectNode objectNode) {
            targets.add(objectNode);
        }
    }

    /**
     * 批量处理版本：后续把“批量收集 id → 批量查询 → 批量回写”的逻辑放到这里。
     *
     * @param targets 需要调整的对象节点集合
     */
    private void doAdjustData(List<ObjectNode> targets) {
        if (Collections.isEmpty(targets)) {
            return;
        }

        // 1) 批量收集：只收集“缺失 name”的 id
        Map<FieldMapping, Set<Long>> idsByMapping = new HashMap<>();
        for (FieldMapping mapping : fieldMappings) {
            idsByMapping.put(mapping, new HashSet<>());
        }

        for (ObjectNode node : targets) {
            for (FieldMapping mapping : fieldMappings) {
                String idStr = idStrIfNeedFill(node, mapping);
                if (Objects.isNull(idStr)) {
                    continue;
                }

                try {
                    idsByMapping.get(mapping).add(Long.parseLong(idStr));
                } catch (Exception ignore) {
                    // id 不是数字，忽略
                }
            }
        }

        // 2) 批量查询：每个字段映射独立查询（不按类型合并）
        Map<FieldMapping, Map<String, String>> namesByMapping = new HashMap<>();
        for (FieldMapping mapping : fieldMappings) {
            Set<Long> ids = idsByMapping.get(mapping);
            if (Collections.isEmpty(ids)) {
                namesByMapping.put(mapping, Map.of());
                continue;
            }

            namesByMapping.put(mapping, mapping.resolver().apply(ids));
        }

        // 3) 批量回写：只写入缺失 name 的字段
        for (ObjectNode node : targets) {
            for (FieldMapping mapping : fieldMappings) {
                String idStr = idStrIfNeedFill(node, mapping);
                if (Objects.isNull(idStr)) {
                    continue;
                }

                Map<String, String> map = namesByMapping.get(mapping);
                if (Objects.isNull(map)) {
                    continue;
                }

                String name = map.get(idStr);
                if (Strings.isBlank(name)) {
                    continue;
                }

                node.put(mapping.nameField(), name);
            }
        }
    }

    /**
     * 根据 ID 字段名 + NAME字段值 决定返回 ID字段值
     *
     * @param node    响应记录
     * @param mapping 属性映射对象
     * @return ID 字段值
     */
    private String idStrIfNeedFill(ObjectNode node, FieldMapping mapping) {
        JsonNode idNode = node.get(mapping.idField());
        if (Objects.isNull(idNode) || idNode.isNull() || !idNode.isValueNode()) {
            return null;
        }

        JsonNode nameNode = node.get(mapping.nameField());
        if (Objects.nonNull(nameNode) && !nameNode.isNull()) {
            return null;
        }

        return idNode.asString();
    }

    /**
     * 响应处理顺序，值越小越先执行。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 900;
    }

    @Override
    public String processorName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 定义一个简单容器，包含 ID 字段名、NAME 字段名、批量解析函数。
     *
     * @param idField   ID 的属性名
     * @param nameField NAME  的属性名
     * @param resolver  批量解析：ids → (id->name)
     */
    private record FieldMapping(String idField, String nameField, Function<Set<Long>, Map<String, String>> resolver) {
    }
}
