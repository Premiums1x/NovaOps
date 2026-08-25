package com.novaops.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

  @NotBlank(message = "账号不能为空")
  @Pattern(regexp = "^[a-zA-Z0-9_]{4,32}$", message = "账号需为 4~32 位字母、数字或下划线")
  private String username;

  @NotBlank(message = "邮箱不能为空")
  @Email(message = "邮箱格式不正确")
  @Size(max = 128, message = "邮箱过长")
  private String email;

  @NotBlank(message = "密码不能为空")
  @Size(min = 8, max = 64, message = "密码长度需在 8~64 位之间")
  private String password;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
