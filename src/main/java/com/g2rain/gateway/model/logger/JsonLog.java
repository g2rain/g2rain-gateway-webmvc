package com.g2rain.gateway.model.logger;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author alpha
 * @since 2026/4/13
 */
@Getter
@Setter
@NoArgsConstructor
public class JsonLog {
    private String level;
    private String traceId;
    private String requestId;
    private long timestamp;
    private String message;
}
