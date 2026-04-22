package com.novaops.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

  private String jwtSecret;
  private long accessTokenExpireSeconds;
  private long refreshTokenExpireDays;

  public String getJwtSecret() {
    return jwtSecret;
  }

  public void setJwtSecret(String jwtSecret) {
    this.jwtSecret = jwtSecret;
  }

  public long getAccessTokenExpireSeconds() {
    return accessTokenExpireSeconds;
  }

  public void setAccessTokenExpireSeconds(long accessTokenExpireSeconds) {
    this.accessTokenExpireSeconds = accessTokenExpireSeconds;
  }

  public long getRefreshTokenExpireDays() {
    return refreshTokenExpireDays;
  }

  public void setRefreshTokenExpireDays(long refreshTokenExpireDays) {
    this.refreshTokenExpireDays = refreshTokenExpireDays;
  }
}
