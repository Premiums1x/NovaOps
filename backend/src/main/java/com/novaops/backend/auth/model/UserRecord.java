package com.novaops.backend.auth.model;

public class UserRecord {

  private String id;
  private String username;
  private String passwordHash;
  private String displayName;
  private String roleId;
  private Boolean enabled;
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

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getRoleId() { return roleId; }
  public void setRoleId(String roleId) { this.roleId = roleId; }
  public Boolean getEnabled() { return enabled; }
  public void setEnabled(Boolean enabled) { this.enabled = enabled; }
  public Boolean getPlatformAdmin() { return platformAdmin; }
  public void setPlatformAdmin(Boolean platformAdmin) { this.platformAdmin = platformAdmin; }
}
