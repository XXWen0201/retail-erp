package com.retail.erp.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限注解
 *
 * 用法：@RequireRole({"ADMIN", "MANAGER"}) 标在 Controller 方法或类上。
 * 鉴权在 AuthInterceptor 里统一完成，业务代码不出现权限判断的 if-else。
 *
 * 约定：ADMIN 默认拥有全部权限，无需在每个注解里重复写 ADMIN。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色列表 */
    String[] value();
}
