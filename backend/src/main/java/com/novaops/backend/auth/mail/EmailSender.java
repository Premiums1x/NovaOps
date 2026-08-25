package com.novaops.backend.auth.mail;

/**
 * 邮件发送抽象：注册/重置密码等场景通过它发送带 token 的验证邮件。
 * 提供多种实现（log 本地降级 / smtp 真实转发），由 app.mail.mode 配置切换。
 */
public interface EmailSender {

  void sendVerificationEmail(String to, String token);

  void sendResetPasswordEmail(String to, String token);
}
