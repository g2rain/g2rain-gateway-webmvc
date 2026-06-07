package com.g2rain.gateway.model.event;


import com.g2rain.common.enums.OrganType;
import com.g2rain.common.enums.SessionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 网关出口侧结构化日志载体（如 Kafka 管道序列化）。
 *
 * <p>设计约定（由写入方如 {@code TraceLoggingFilter} 填充）：</p>
 *
 * @author alpha
 * @since 2026/4/13
 */
@Getter
@Setter
@NoArgsConstructor
public class GatewayEvent {
    /**
     * 网关跟踪标识
     * <p>用于链路追踪，便于日志收集和问题定位。</p>
     */
    private String traceId;

    /**
     * 客户端标识
     * <p>标识请求的客户端应用。DPoP 的密钥 k(ey)id, 用于标识绑定的 DPoP 密钥，便于后端验证签名</p>
     */
    private String clientId;

    /**
     * 前端请求标识
     * <p>唯一标识一次请求，方便追踪和调试。</p>
     */
    private String requestId;

    /**
     * 前端请求时间
     * <p>请求发起时间戳，用于性能统计和延迟分析。</p>
     */
    private String requestTime;

    /**
     * 当前请求的 acceptLanguage
     */
    private String acceptLanguage;

    /**
     * 请求路径
     */
    private String path;

    /**
     * 请求方法
     */
    private String method;

    /**
     * User-Agent：客户端标识（浏览器/设备类型、版本）
     */
    private String userAgent;

    /**
     * Host：请求的目标主机名和端口号（用于虚拟主机路由）
     */
    private String host;

    /**
     * X-Forwarded-For：事实标准，记录代理链中的客户端真实 IP 列表（client, proxy1, proxy2...）
     */
    private String xForwardedFor;

    /**
     * X-Real-IP：非标准头部（由 Nginx 等普及），只存放当前代理传递的单一真实客户端 IP
     */
    private String xRealIp;

    /**
     * Referer：请求来源页面 URL（用于防盗链、流量分析，注意拼写少一个'r'）
     */
    private String referer;

    /**
     * 会话类型
     * <p>标识当前会话类型，例如用户会话、应用会话等。</p>
     */
    private SessionType sessionType;

    /**
     * 账号 ID
     */
    private Long passportId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 真实姓名
     * <p>用于展示或身份确认。</p>
     */
    private String name;

    /**
     * 公司内管理员标记位
     * <p>标识当前用户是否为管理员。</p>
     */
    private boolean adminUser;

    /**
     * 组织标识
     * <p>当前用户所属组织的唯一标识。</p>
     */
    private Long organId;

    /**
     * 组织名称
     * <p>当前用户所属组织的名称。</p>
     */
    private String organName;

    /**
     * 组织类型
     * <p>标识当前用户所属组织类型。</p>
     */
    private OrganType organType;

    /**
     * 平台管理组织标记位
     * <p>标识该组织是否为平台管理组织。</p>
     */
    private boolean adminCompany;

    /**
     * 数据操作的目标组织标识
     * <p>数据操作的目标组织的唯一标识。</p>
     */
    private Long targetOrganId;

    /**
     * 请求来源应用标识
     * <p>表示当前接口调用是由哪个应用发起的</p>
     */
    private Long applicationId;

    /**
     * 请求所属应用编码
     */
    private String applicationCode;

    /**
     * 请求来源应用所属机构标识
     * <p>表示发起当前接口调用的应用所隶属的机构</p>
     */
    private Long applicationOrganId;

    /**
     * 请求 / 响应摘要，整体序列化形态接近
     * Request queryString, body {"a":[1,2]} queryString 和 body 合并
     * {"payload":{"request":{}, "response":{}}}
     */
    private String payload;

    /**
     * 创建 {@link EventBuilder}，用于链式填充后 {@link EventBuilder#build()}。
     */
    public static EventBuilder builder() {
        return new EventBuilder();
    }
}
