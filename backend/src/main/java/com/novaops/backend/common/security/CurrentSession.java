package com.novaops.backend.common.security;

public class CurrentSession {

  private final String userId;
  private final String username;
  private final String displayName;
  private final String tenantId;

  public CurrentSession(String userId, String username, String displayName, String tenantId) {
    this.userId = userId;
    this.username = username;
    this.displayName = displayName;
    this.tenantId = tenantId;
  }

  public String getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getTenantId() {
    return tenantId;
  }
}
