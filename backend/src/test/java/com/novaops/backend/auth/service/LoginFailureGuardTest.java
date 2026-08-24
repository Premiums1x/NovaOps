package com.novaops.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novaops.backend.common.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LoginFailureGuardTest {

  private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-08-24T10:00:00Z"));
  private final LoginFailureGuard guard = new LoginFailureGuard(new Clock() {
    @Override
    public Instant instant() {
      return now.get();
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }
  });

  @Test
  void staysOpenBelowThreshold() {
    for (int i = 0; i < LoginFailureGuard.MAX_FAILURES - 1; i++) {
      guard.recordFailure("alice");
    }
    assertThatCode(() -> guard.assertNotLocked("alice")).doesNotThrowAnyException();
  }

  @Test
  void locksAfterConsecutiveFailures() {
    for (int i = 0; i < LoginFailureGuard.MAX_FAILURES; i++) {
      guard.recordFailure("bob");
    }
    assertThatThrownBy(() -> guard.assertNotLocked("bob"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("临时锁定");
  }

  @Test
  void lockExpiresAfterDuration() {
    for (int i = 0; i < LoginFailureGuard.MAX_FAILURES; i++) {
      guard.recordFailure("carol");
    }
    now.set(now.get().plus(LoginFailureGuard.LOCK_DURATION).plusSeconds(1));
    assertThatCode(() -> guard.assertNotLocked("carol")).doesNotThrowAnyException();
  }

  @Test
  void successfulLoginResetsCounter() {
    for (int i = 0; i < LoginFailureGuard.MAX_FAILURES - 1; i++) {
      guard.recordFailure("dave");
    }
    guard.reset("dave");
    for (int i = 0; i < LoginFailureGuard.MAX_FAILURES - 1; i++) {
      guard.recordFailure("dave");
    }
    assertThatCode(() -> guard.assertNotLocked("dave")).doesNotThrowAnyException();
  }

  @Test
  void lockDurationIsTenMinutes() {
    assertThat(LoginFailureGuard.LOCK_DURATION).isEqualTo(Duration.ofMinutes(10));
  }
}
