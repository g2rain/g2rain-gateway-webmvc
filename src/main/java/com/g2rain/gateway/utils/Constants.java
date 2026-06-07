package com.g2rain.gateway.utils;


/**
 * <p>{@code Constants} 类定义了系统中使用的常量值，主要用于 HTTP 请求头和认证类型的统一管理。</p>
 * <p>
 * 所有字段均为静态常量，方便在全局使用，避免硬编码。
 * </p>
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * String authHeader = Constants.AUTHORIZATION_HEADER;
 * String proofHeader = Constants.CLIENT_PROOF_HEADER;
 * String jwtType = Constants.CLIENT_PROOF_JWT_TYPE;
 * }</pre>
 *
 * @author alpha
 * @since 2025/10/6
 */
public final class Constants {

    /**
     * 私有构造，禁止实例化
     */
    private Constants() {

    }

    /**
     * {@code DEBUG_KEY_HEADER} 开启 DEBUG 模式的请求头KEY
     */
    public static final String DEBUG_KEY_HEADER = "X-DEBUG-KEY";

    /**
     * {@code AUTHORIZATION_HEADER} HTTP 请求头：Authorization
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * Authorization 中 Bearer 方案前缀（与 OpenAI 一致：{@code Bearer sk-...}）。
     */
    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * 静态 API Key 固定总长度。
     */
    public static final int API_KEY_LENGTH = 64;

    /**
     * {@code CLIENT_PROOF_HEADER} HTTP 请求头：DPoP
     */
    public static final String CLIENT_PROOF_HEADER = "DPoP";

    /**
     * DPoP proof 类型标识。
     *
     * <p>用于区分普通 JWT 与  DPoP proof， DPoP proof 必须在 Header 中
     * 使用 {@code typ="dpop+jwt"} 来表明其类型，以便鉴权组件正确识别并进行
     * 防重放和请求绑定校验。</p>
     */
    public static final String CLIENT_PROOF_JWT_TYPE = "dpop+jwt";

    /**
     * 路由匹配命中后, 缓存路由上下文的键
     */
    public static final String ROUTE_CONTEXT_PATH = "route.context.path";

    /**
     * 路由匹配命中后, 缓存路由标识的键
     */
    public static final String ROUTE_INTERNAL_ID = "route.internal.id";

    /**
     * 请求标识的键
     */
    public static final String REQUEST_ID = "requestId";

    /**
     * 接口文档路径
     */
    public static final String DOC_PATH = "/v3/api-docs";

    /**
     * 业务服务接口文档路径
     */
    public static final String DOC_PATH_FORMAT = "/%s" + DOC_PATH;

    /**
     * 请求参数缓存键
     */
    public static final String REQ_BODY_ATTRIBUTE = "g2rain.gateway.request.body";

    /**
     * 标记当前请求已执行 TraceLogging 请求侧日志，响应侧仅在此标记存在时记录（与 WebFlux 成对语义对齐）。
     */
    public static final String TRACE_LOGGING_ACTIVE = "g2rain.gateway.trace.logging.active";

    /**
     * 空响应结果
     */
    public static final String EMPTY_RSP_BODY = "{}";
}
