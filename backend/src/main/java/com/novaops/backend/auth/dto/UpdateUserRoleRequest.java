package com.novaops.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateUserRoleRequest {
  @NotBlank private String roleId;
  public String getRoleId() { return roleId; }
  public void setRoleId(String roleId) { this.roleId = roleId; }
}
