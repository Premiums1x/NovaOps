package com.novaops.backend.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地降级实现：不依赖任何外部邮件服务，把激活/重置链接打印到后端日志。
 * 适合本地开发与简历项目演示——启动后注册，终端即可看到激活链接。
 */
@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "log", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

  @Override
  public void sendVerificationEmail(String to, String token) {
    log.info("[激活邮件] 收件人={} 激活链接=http://localhost:5173/verify?token={}", to, token);
  }

  @Override
  public void sendResetPasswordEmail(String to, String token) {
    log.info("[重置密码邮件] 收件人={} 重置链接=http://localhost:5173/reset-password?token={}", to, token);
  }
}
