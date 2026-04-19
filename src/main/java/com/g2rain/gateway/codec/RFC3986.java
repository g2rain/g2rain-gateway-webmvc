package com.g2rain.gateway.codec;


/**
 * <p>{@code RFC3986} 定义了符合 <a href="https://datatracker.ietf.org/doc/html/rfc3986">RFC 3986</a> 的 URL 编码规范。</p>
 * <p>
 * 提供了多个 {@link PercentCodec} 常量，代表 URL 不同部分的安全字符集，
 * 方便对 URL 各部分进行百分号编码（Percent-Encoding）。
 * </p>
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * String encodedSegment = RFC3986.SEGMENT.encode("hello world:测试", StandardCharsets.UTF_8, Set.of());
 * System.out.println(encodedSegment);
 * }</pre>
 *
 * <ul>
 *     <li>{@link #GEN_DELIMITERS} - 通用分隔符</li>
 *     <li>{@link #SUB_DELIMITERS} - 子分隔符</li>
 *     <li>{@link #RESERVED} - 所有保留字符</li>
 *     <li>{@link #UNRESERVED} - 所有非保留字符</li>
 *     <li>{@link #P_CHAR} - URL path segment 中允许的字符</li>
 *     <li>{@link #SEGMENT} - 单个 path segment</li>
 *     <li>{@link #SEGMENT_NZ_NC} - 非空、非冒号 path segment</li>
 *     <li>{@link #PATH} - URL path 部分</li>
 *     <li>{@link #QUERY} - URL query 部分</li>
 *     <li>{@link #FRAGMENT} - URL fragment 部分</li>
 *     <li>{@link #QUERY_PARAM_VALUE} - query 参数值</li>
 *     <li>{@link #QUERY_PARAM_VALUE_STRICT} - 严格模式 query 参数值</li>
 *     <li>{@link #QUERY_PARAM_NAME} - query 参数名</li>
 *     <li>{@link #QUERY_PARAM_NAME_STRICT} - 严格模式 query 参数名</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/6
 */
public class RFC3986 {
    /**
     * 私有构造，禁止实例化
     */
    private RFC3986() {

    }

    /**
     * URL 中非保留字符集合
     */
    private static final String UNRESERVED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

    /**
     * 通用分隔符（GEN-delims）
     */
    public static final PercentCodec GEN_DELIMITERS = PercentCodec.of(":/?#[]@");

    /**
     * 子分隔符（sub-delims）
     */
    public static final PercentCodec SUB_DELIMITERS = PercentCodec.of("!$&'()*+,;=");

    /**
     * 所有保留字符
     */
    public static final PercentCodec RESERVED = GEN_DELIMITERS.orNew(SUB_DELIMITERS);

    /**
     * 所有非保留字符
     */
    public static final PercentCodec UNRESERVED = PercentCodec.of(UNRESERVED_CHARS);

    /**
     * URL path segment 中允许的字符
     */
    public static final PercentCodec P_CHAR = UNRESERVED.orNew(SUB_DELIMITERS).or(PercentCodec.of(":@"));

    /**
     * 单个 path segment
     */
    public static final PercentCodec SEGMENT = P_CHAR;

    /**
     * 非空、非冒号 path segment
     */
    public static final PercentCodec SEGMENT_NZ_NC = PercentCodec.of(SEGMENT).removeSafe(':');

    /**
     * URL path 部分
     */
    public static final PercentCodec PATH = SEGMENT.orNew(PercentCodec.of("/"));

    /**
     * URL query 部分
     */
    public static final PercentCodec QUERY = P_CHAR.orNew(PercentCodec.of("/?"));

    /**
     * URL fragment 部分
     */
    public static final PercentCodec FRAGMENT = QUERY;

    /**
     * query 参数值
     */
    public static final PercentCodec QUERY_PARAM_VALUE = PercentCodec.of(QUERY).removeSafe('&');

    /**
     * 严格模式 query 参数值
     */
    public static final PercentCodec QUERY_PARAM_VALUE_STRICT = UNRESERVED;

    /**
     * query 参数名
     */
    public static final PercentCodec QUERY_PARAM_NAME = PercentCodec.of(QUERY_PARAM_VALUE).removeSafe('=');

    /**
     * 严格模式 query 参数名
     */
    public static final PercentCodec QUERY_PARAM_NAME_STRICT = UNRESERVED;
}
