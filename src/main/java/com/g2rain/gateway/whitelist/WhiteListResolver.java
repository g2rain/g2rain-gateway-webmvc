package com.g2rain.gateway.whitelist;


import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.config.GatewayWhiteList;
import com.g2rain.gateway.utils.Constants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@code WhiteListResolver} 用于解析网关白名单配置，判断请求是否需要被忽略执行特定 Filter。
 * <p>
 * 白名单支持三种匹配方式：
 * <ul>
 *     <li>contextPaths：按上下文路径匹配</li>
 *     <li>exactPaths：按精确路径匹配</li>
 *     <li>patternPaths：按路径模式匹配（支持通配符）</li>
 * </ul>
 * <p>
 * 优先判断 Filter 白名单，若不匹配则判断全局白名单。
 * </p>
 *
 * @author alpha
 * @since 2025/10/8
 */
@Component
@AllArgsConstructor
public class WhiteListResolver {

    /**
     * 用于解析路径模式的工具，支持类似 Ant 风格的通配符匹配。
     */
    private final PathPatternParser patternParser = new PathPatternParser();

    /**
     * 网关白名单配置，包含全局白名单和各 Filter 的白名单规则。
     */
    private final GatewayWhiteList gatewayWhiteList;

    /**
     * 判断某个请求是否需要被忽略执行特定 Filter（即是否命中白名单）。
     * <p>
     * 匹配优先级：
     * <ol>
     *     <li>先检查指定 Filter 的白名单规则（细粒度）</li>
     *     <li>若不匹配，再检查全局白名单规则（通用规则）</li>
     * </ol>
     * 匹配方式包括：contextPath、exactPath、patternPath。
     *
     * @param filterName 过滤器名称（通常为类名）
     * @param request    当前请求上下文
     * @return true 如果请求命中白名单，应忽略该 Filter；false 否则
     */
    public boolean shouldExclude(String filterName, ServerRequest request) {
        return shouldExclude(filterName, request.servletRequest());
    }

    /**
     * 判断某个请求是否需要被忽略执行特定 Filter（Servlet 请求版本）。
     *
     * @param filterName 过滤器名称（通常为类名）
     * @param request    当前请求上下文
     * @return true 如果请求命中白名单，应忽略该 Filter；false 否则
     */
    public boolean shouldExclude(String filterName, HttpServletRequest request) {
        // 获取请求的上下文路径（context path），例如 basis 等
        String contextPath = Objects.toString(request.getAttribute(Constants.ROUTE_CONTEXT_PATH), null);
        // 获取请求的完整 API 路径，例如 /basis/user/login
        String apiPath = request.getRequestURI();
        // 优先判断 Filter 白名单
        Map<String, GatewayWhiteList.WhiteList> filters = gatewayWhiteList.getFilters();
        if (Objects.nonNull(filters) && matches(filters.get(filterName), contextPath, apiPath)) {
            return true;
        }

        // 再判断全局白名单
        return matches(gatewayWhiteList.getGlobal(), contextPath, apiPath);
    }

    /**
     * 核心白名单匹配方法。
     * <p>
     * 检查 contextPaths、exactPaths、patternPaths 是否匹配请求路径。
     *
     * @param whiteList   白名单规则对象
     * @param contextPath 请求上下文路径
     * @param apiPath     请求 API 路径
     * @return true 如果匹配，false 否则
     */
    private boolean matches(GatewayWhiteList.WhiteList whiteList, String contextPath, String apiPath) {
        // 如果白名单对象为空，则直接返回不匹配
        if (Objects.isNull(whiteList)) {
            return false;
        }

        // contextPath 匹配：判断请求的上下文路径是否在白名单 contextPaths 集合中
        Set<String> contextPaths = whiteList.getContextPaths();
        if (Objects.nonNull(contextPaths) && contextPaths.contains(contextPath)) {
            return true;
        }

        // exactPath 精确匹配：判断请求路径是否在白名单 exactPaths 集合中
        Set<String> exactPaths = whiteList.getExactPaths();
        if (Objects.nonNull(exactPaths) && exactPaths.contains(apiPath)) {
            return true;
        }

        // patternPath 模式匹配：判断请求路径是否匹配白名单 patternPaths 中的任意模式
        Set<String> patternPaths = whiteList.getPatternPaths();
        if (Collections.isEmpty(patternPaths)) {
            return false; // 没有模式路径，直接返回不匹配
        }

        // 将请求路径解析为 PathContainer，便于模式匹配
        PathContainer pathContainer = PathContainer.parsePath(apiPath);

        for (String pattern : patternPaths) {
            // 忽略空白模式
            if (Strings.isBlank(pattern)) {
                continue;
            }

            // 解析路径模式
            PathPattern pathPattern = patternParser.parse(pattern);

            // 如果匹配成功，直接返回 true
            if (pathPattern.matches(pathContainer)) {
                return true;
            }
        }

        // 所有匹配方式都不满足，则返回 false
        return false;
    }
}
