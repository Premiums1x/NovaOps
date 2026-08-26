package com.novaops.backend.auth.dto;

/**
 * 注册响应：log 本地降级模式下携带 activationToken，前端可直接跳转激活页完成激活；
 * 真实 SMTP 模式下 activationToken 为 null，激活凭证只通过邮件下发，
 * 避免冒用他人邮箱注册时被注册者自己激活。
 */
public class RegisterResponse {

  private String activationToken;

  public RegisterResponse() {
  }

  public RegisterResponse(String activationToken) {
    this.activationToken = activationToken;
  }

  public String getActivationToken() {
    return activationToken;
  }

  public void setActivationToken(String activationToken) {
    this.activationToken = activationToken;
  }
}