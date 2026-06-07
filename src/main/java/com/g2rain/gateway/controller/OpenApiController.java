package com.g2rain.gateway.controller;

import com.g2rain.basis.dto.ServiceRegistrySelectDto;
import com.g2rain.basis.vo.ServiceRegistryVo;
import com.g2rain.common.model.Result;
import com.g2rain.gateway.client.ServiceRegistryClient;
import com.g2rain.gateway.utils.Constants;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 动态 OpenAPI 目录：数据源自 {@link ServiceRegistryClient#selectList(ServiceRegistrySelectDto)}（与 WebFlux 侧 {@code OpenApiController} + {@code MemoryRouteRepository} 等效）。
 *
 * @author alpha
 * @since 2026/4/11
 */
@RestController
@AllArgsConstructor
public class OpenApiController {

    private final ServiceRegistryClient serviceRegistryClient;

    /**
     * Swagger UI {@code configUrl} 所需结构（含 {@code urls} 与默认选中的 {@code urls.primaryName}）。
     */
    @GetMapping(value = "/swagger-ui-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> swaggerUiConfig() {
        List<OpenApiDocItem> items = listDocs();
        List<Map<String, String>> urls = new ArrayList<>(items.size());
        for (OpenApiDocItem item : items) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", item.name());
            row.put("url", item.url());
            urls.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("urls", urls);
        if (!items.isEmpty()) {
            body.put("urls.primaryName", items.getFirst().name());
        }

        return body;
    }

    /**
     * 返回当前动态路由推导出的文档列表；同一 context 多条路由只保留一条。
     */
    private List<OpenApiDocItem> listDocs() {
        Map<String, OpenApiDocItem> byUrl = new LinkedHashMap<>();

        Result<List<ServiceRegistryVo>> result = serviceRegistryClient.selectList(
            new ServiceRegistrySelectDto()
        );

        if (Objects.isNull(result) || !result.isSuccess()) {
            return List.of();
        }

        for (ServiceRegistryVo v : result.getData()) {
            OpenApiDocItem item = toItem(v);
            if (Objects.isNull(item)) {
                continue;
            }

            byUrl.putIfAbsent(item.url(), item);
        }

        return byUrl.values().stream()
            .sorted(Comparator.comparing(OpenApiDocItem::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private OpenApiDocItem toItem(ServiceRegistryVo registry) {
        return Optional.of(registry.getRoutePrefix())
            .map(ctx -> new OpenApiDocItem(ctx, String.format(Constants.DOC_PATH_FORMAT, ctx)))
            .orElse(null);
    }

    public record OpenApiDocItem(String name, String url) {
    }
}
