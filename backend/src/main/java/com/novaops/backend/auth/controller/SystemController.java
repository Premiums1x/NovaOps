package com.novaops.backend.auth.controller;

import com.novaops.backend.auth.dto.CreateInvitationRequest;
import com.novaops.backend.auth.dto.CreatedInvitationResponse;
import com.novaops.backend.auth.dto.CreateTenantRequest;
import com.novaops.backend.auth.dto.InvitationResponse;
import com.novaops.backend.auth.dto.TenantResponse;
import com.novaops.backend.auth.service.SystemService;
import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.security.RequestContext;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

  private final SystemService systemService;

  public SystemController(SystemService systemService) {
    this.systemService = systemService;
  }

  @GetMapping("/tenants")
  public ApiResponse<List<TenantResponse>> tenants() {
    return ApiResponse.success(systemService.listTenants(RequestContext.getRequired()));
  }

  @PostMapping("/tenants")
  public ApiResponse<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
    return ApiResponse.success(systemService.createTenant(RequestContext.getRequired(), request), "租户创建成功");
  }

  @GetMapping("/invitations")
  public ApiResponse<List<InvitationResponse>> invitations() {
    return ApiResponse.success(systemService.listInvitations(RequestContext.getRequired()));
  }

  @PostMapping("/invitations")
  public ApiResponse<CreatedInvitationResponse> createInvitation(@Valid @RequestBody CreateInvitationRequest request) {
    return ApiResponse.success(systemService.createInvitation(RequestContext.getRequired(), request), "邀请创建成功");
  }
}
