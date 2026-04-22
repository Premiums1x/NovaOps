package com.novaops.backend.common.security;

import com.novaops.backend.common.config.SecurityProperties;
import com.novaops.backend.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecurityProperties securityProperties;
  private final SecretKey secretKey;

  public JwtService(SecurityProperties securityProperties) {
    this.securityProperties = securityProperties;
    this.secretKey = Keys.hmacShaKeyFor(securityProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
  }

  public String createAccessToken(CurrentSession session) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(securityProperties.getAccessTokenExpireSeconds());

    return Jwts.builder()
        .subject(session.getUserId())
        .claim("username", session.getUsername())
        .claim("displayName", session.getDisplayName())
        .claim("tenantId", session.getTenantId())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .signWith(secretKey)
        .compact();
  }

  public CurrentSession parseAccessToken(String token) {
    try {
      Claims claims = Jwts.parser()
          .verifyWith(secretKey)
          .build()
          .parseSignedClaims(token)
          .getPayload();

      return new CurrentSession(
          claims.getSubject(),
          claims.get("username", String.class),
          claims.get("displayName", String.class),
          claims.get("tenantId", String.class)
      );
    } catch (Exception exception) {
      throw new BusinessException(401, "token 无效");
    }
  }

  public long getAccessTokenExpireSeconds() {
    return securityProperties.getAccessTokenExpireSeconds();
  }

  public long getRefreshTokenExpireDays() {
    return securityProperties.getRefreshTokenExpireDays();
  }
}
