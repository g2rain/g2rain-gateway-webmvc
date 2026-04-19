/**
 * 接口权限领域模块
 *
 * <p>
 * 该包负责“权限规则管理 + 请求权限匹配”：
 * </p>
 * <ul>
 *     <li>规则模型：{@link com.g2rain.gateway.permission.ApiPermissionRule}</li>
 *     <li>匹配服务：{@link com.g2rain.gateway.permission.ApiPermissionService}</li>
 * </ul>
 *
 * <p>
 * 实现上复用 {@link com.g2rain.gateway.matcher} 作为通用匹配基座，
 * 将权限场景的业务语义（应用编码是否可访问接口）封装在 service 层。
 * </p>
 */
package com.g2rain.gateway.permission;

