package com.novaops.backend.common.security;

import com.novaops.backend.auth.mapper.AuthMapper;
import com.novaops.backend.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 在 AuthInterceptor 之后执行，按 @RequirePermission 注解校验当前会话在
 * 当前租户下是否持有权限码。后端必须与前端按钮级鉴权使用同一套权限码，
 * 否则绕过界面直接调用接口即可越权。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

  private final AuthMapper authMapper;

  public PermissionInterceptor(AuthMapper authMapper) {
    this.authMapper = authMapper;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }

    RequirePermission required = handlerMethod.getMethodAnnotation(RequirePermission.class);
    if (required == null || required.value().isBlank()) {
      return true;
    }

    CurrentSession session = RequestContext.getRequired();
    if (!authMapper.listPermissions(session.getUserId()).contains(required.value())) {
      throw new BusinessException(403, "无权限执行该操作");
    }
    return true;
  }
}
