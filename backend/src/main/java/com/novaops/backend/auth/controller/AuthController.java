package com.novaops.backend.auth.controller;

import com.novaops.backend.auth.dto.AuthTokenResponse;
import com.novaops.backend.auth.dto.LoginRequest;
import com.novaops.backend.auth.dto.LoginResponse;
import com.novaops.backend.auth.dto.MenuDataResponse;
import com.novaops.backend.auth.dto.RefreshTokenRequest;
import com.novaops.backend.auth.dto.SwitchTenantRequest;
import com.novaops.backend.auth.dto.UserProfileResponse;
import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.security.RequestContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.success(authService.login(request), "登录成功");
  }

  @PostMapping("/refresh")
  public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return ApiResponse.success(authService.refresh(request), "刷新成功");
  }

  @PostMapping("/switch-tenant")
  public ApiResponse<LoginResponse> switchTenant(@Valid @RequestBody SwitchTenantRequest request) {
    return ApiResponse.success(authService.switchTenant(RequestContext.getRequired(), request), "租户切换成功");
  }

  @GetMapping("/me")
  public ApiResponse<UserProfileResponse> me() {
    return ApiResponse.success(authService.me(RequestContext.getRequired()));
  }

  @GetMapping("/menu")
  public ApiResponse<MenuDataResponse> menu() {
    return ApiResponse.success(authService.menu(RequestContext.getRequired()));
  }
}
