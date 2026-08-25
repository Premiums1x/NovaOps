package com.novaops.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class SwitchTenantRequest {

  @NotBlank(message = "tenantId 不能为空")
  private String tenantId;

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }
}
