package com.novaops.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateTenantRequest {
  @NotBlank(message = "租户代码不能为空")
  @Size(max = 64, message = "租户代码不能超过64个字符")
  @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "租户代码必须以小写英文字母开头，且只能包含小写英文字母、数字和连字符")
  private String code;

  @NotBlank(message = "租户名称不能为空")
  @Size(max = 100, message = "租户名称不能超过100个字符")
  private String name;

  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
}
