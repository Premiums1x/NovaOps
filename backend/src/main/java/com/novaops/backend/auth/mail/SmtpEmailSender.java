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

  public SmtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from:}") String from) {
    this.mailSender = mailSender;
    this.from = from;
  }

  @Override
  public void sendVerificationEmail(String to, String token) {
    send(to, "NovaOps 邮箱验证", "点击链接激活账号：http://localhost:5173/verify?token=" + token);
  }

  @Override
  public void sendResetPasswordEmail(String to, String token) {
    send(to, "NovaOps 密码重置", "点击链接重置密码：http://localhost:5173/reset-password?token=" + token);
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
