package com.g2rain.gateway.model.cache;


import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class AppIdName {

    /**
     * 应用 ID
     */
    private Long id;

    /**
     * 应用名称
     */
    private String applicationName;
}
