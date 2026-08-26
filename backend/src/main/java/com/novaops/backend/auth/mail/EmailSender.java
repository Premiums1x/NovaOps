package com.novaops.backend.auth.mail;

/**
 * 邮件发送抽象：注册/重置密码等场景通过它发送带 token 的验证邮件。
 * 提供多种实现（log 本地降级 / smtp 真实转发），由 app.mail.mode 配置切换。
 */
public interface EmailSender {

  void sendVerificationEmail(String to, String token);

  void sendResetPasswordEmail(String to, String token);

  /**
   * 是否允许把激活凭证直接交回前端：log 本地降级模式返回原 token 供联调直接激活；
   * 真实发信（smtp）模式返回 null，激活凭证只通过邮件下发。
   */
  default String activationTokenFor(String token) {
    return null;
  }
}
