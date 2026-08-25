package com.novaops.backend.auth.dto;

import java.util.List;

public class UserProfileResponse {

  private String id;
  private String username;
  private String displayName;
  private List<String> roles;
  private List<String> permissions;
  private String tenantId;
  private List<TenantInfoResponse> tenants;
  private Boolean platformAdmin;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles;
  }

  public List<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(List<String> permissions) {
    this.permissions = permissions;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public List<TenantInfoResponse> getTenants() {
    return tenants;
  }

  public void setTenants(List<TenantInfoResponse> tenants) {
    this.tenants = tenants;
  }

  public Boolean getPlatformAdmin() {
    return platformAdmin;
  }

  public void setPlatformAdmin(Boolean platformAdmin) {
    this.platformAdmin = platformAdmin;
  }
}
