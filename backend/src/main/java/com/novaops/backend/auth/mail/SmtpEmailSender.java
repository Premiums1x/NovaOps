package com.novaops.backend.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 真实 SMTP 转发实现：通过 JavaMailSender 发送验证邮件。
 * 仅当 app.mail.mode=smtp 时激活，需配置 spring.mail.* 与 app.mail.from。
 */
@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

  private final JavaMailSender mailSender;
  private final String from;
  private final String activationBaseUrl;

  public SmtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from:}") String from,
      @Value("${app.mail.activation-base-url:http://localhost:5173}") String activationBaseUrl) {
    this.mailSender = mailSender;
    this.from = from;
    this.activationBaseUrl = activationBaseUrl;
  }

  @Override
  public void sendVerificationEmail(String to, String token) {
    send(to, "NovaOps 邮箱验证", "点击链接激活账号：" + activationBaseUrl + "/verify?token=" + token);
  }

  @Override
  public void sendResetPasswordEmail(String to, String token) {
    send(to, "NovaOps 密码重置", "点击链接重置密码：" + activationBaseUrl + "/reset-password?token=" + token);
  }

  private void send(String to, String subject, String text) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(text);
    mailSender.send(message);
  }
}
