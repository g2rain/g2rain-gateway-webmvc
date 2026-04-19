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
public class OrganIdName {
    /**
     * 机构 ID
     */

    private Long organId;

    /**
     * 机构名称
     */
    private String organName;
}
