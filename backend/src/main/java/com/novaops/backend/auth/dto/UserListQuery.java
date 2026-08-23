package com.novaops.backend.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class UserListQuery {
  @Min(1) private int page = 1;
  @Min(1) @Max(100) private int pageSize = 10;
  private String keyword;
  private String roleId;
  private Boolean enabled;
  public int getPage() { return page; }
  public void setPage(int page) { this.page = page; }
  public int getPageSize() { return pageSize; }
  public void setPageSize(int pageSize) { this.pageSize = pageSize; }
  public String getKeyword() { return keyword; }
  public void setKeyword(String keyword) { this.keyword = keyword; }
  public String getRoleId() { return roleId; }
  public void setRoleId(String roleId) { this.roleId = roleId; }
  public Boolean getEnabled() { return enabled; }
  public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
