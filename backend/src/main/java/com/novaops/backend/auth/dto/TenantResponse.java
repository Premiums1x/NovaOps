package com.novaops.backend.auth.dto;

public class TenantResponse {
  private String code;
  private String name;

  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
}
