package com.g2rain.gateway.model.cache;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 接口权限值对象（VO）。
 * <p>
 * 用于前后端交互中传递接口的基本信息及权限标识，
 * 包括接口名称、路径、请求方法和分类标签。
 * </p>
 *
 * @author alpha
 * @since 2026/1/15
 */
@Setter
@Getter
@NoArgsConstructor
public class BaseAuthority {

    /**
     * 资源接口标识
     */
    private Long id;

    /**
     * 接口地址状态
     */
    private String status;
}
