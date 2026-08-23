package com.novaops.backend.auth.dto;

public class RoleResponse {

  private String id;
  private String code;
  private String name;
  private String description;
  private java.util.List<String> permissions;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public java.util.List<String> getPermissions() { return permissions; }
  public void setPermissions(java.util.List<String> permissions) { this.permissions = permissions; }
}
