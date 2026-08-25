package com.novaops.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
  @NotBlank(message = "邀请令牌不能为空")
  private String invitationToken;

  @NotBlank(message = "用户名不能为空")
  @Size(max = 64, message = "用户名不能超过64个字符")
  private String username;

  @NotBlank(message = "显示名称不能为空")
  @Size(max = 100, message = "显示名称不能超过100个字符")
  private String displayName;

  @NotBlank(message = "密码不能为空")
  @Size(min = 6, max = 72, message = "密码长度必须为6到72个字符")
  private String password;

  public String getInvitationToken() { return invitationToken; }
  public void setInvitationToken(String invitationToken) { this.invitationToken = invitationToken; }
  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }
  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }
}
