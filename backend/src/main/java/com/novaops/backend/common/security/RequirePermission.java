package com.novaops.backend.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口所需的权限码，由 {@link PermissionInterceptor} 在认证之后统一校验。
 * 权限按「身份 × 租户」矩阵（sys_role_permission）判定，与前端下发的权限码同源。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

  /** 需要的权限码，如 ticket:view；缺省表示仅需登录 */
  String value() default "";
}
