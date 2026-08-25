package com.novaops.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateInvitationRequest {
  @NotBlank(message = "租户不能为空")
  private String tenantId;

  @NotBlank(message = "邀请身份不能为空")
  private String roleCode;

  public String getTenantId() { return tenantId; }
  public void setTenantId(String tenantId) { this.tenantId = tenantId; }
  public String getRoleCode() { return roleCode; }
  public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
}
