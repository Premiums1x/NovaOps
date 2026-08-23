package com.novaops.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novaops.backend.common.config.SecurityProperties;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  @Test
  void refusesToStartWithShortSecret() {
    assertThatThrownBy(() -> new JwtService(properties("short-secret")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("NOVAOPS_JWT_SECRET");
    assertThatThrownBy(() -> new JwtService(properties(null)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void accessTokenCarriesTenantAndUserClaims() {
    JwtService jwtService = new JwtService(properties("unit-test-secret-0123456789abcdef0123456789"));
    CurrentSession session = new CurrentSession("u-1", "alice", "Alice", "tenant-a");

    CurrentSession parsed = jwtService.parseAccessToken(jwtService.createAccessToken(session));

    assertThat(parsed.getUserId()).isEqualTo("u-1");
    assertThat(parsed.getUsername()).isEqualTo("alice");
    assertThat(parsed.getTenantId()).isEqualTo("tenant-a");
  }

  @Test
  void rejectsTokenSignedWithDifferentSecret() {
    JwtService signer = new JwtService(properties("unit-test-secret-0123456789abcdef0123456789"));
    JwtService verifier = new JwtService(properties("another-test-secret-0123456789abcdef01234"));
    String token = signer.createAccessToken(new CurrentSession("u-1", "alice", "Alice", "tenant-a"));

    assertThatThrownBy(() -> verifier.parseAccessToken(token))
        .hasMessageContaining("token");
  }

  private SecurityProperties properties(String secret) {
    SecurityProperties properties = new SecurityProperties();
    properties.setJwtSecret(secret);
    properties.setAccessTokenExpireSeconds(1800);
    properties.setRefreshTokenExpireDays(7);
    return properties;
  }
}
