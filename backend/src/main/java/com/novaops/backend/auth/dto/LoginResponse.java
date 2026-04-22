package com.novaops.backend.auth.dto;

public class LoginResponse extends AuthTokenResponse {

  private String tenantId;

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }
}
