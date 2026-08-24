package com.novaops.backend.auth.service;

import com.novaops.backend.auth.mapper.AuthMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期清理已过期与已撤销的 refresh token，避免 sys_refresh_token 无限增长。
 */
@Component
public class RefreshTokenCleanupTask {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupTask.class);

  private final AuthMapper authMapper;

  public RefreshTokenCleanupTask(AuthMapper authMapper) {
    this.authMapper = authMapper;
  }

  @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
  public void purgeStaleTokens() {
    try {
      int deleted = authMapper.deleteStaleRefreshTokens();
      if (deleted > 0) {
        log.info("purged {} stale refresh tokens", deleted);
      }
    } catch (Exception ex) {
      log.warn("refresh token cleanup failed: {}", ex.getMessage());
    }
  }
}
