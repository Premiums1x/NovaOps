package com.novaops.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {
  @NotBlank @Size(min = 6, max = 72) private String password;
  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }
}
