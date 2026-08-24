package com.novaops.backend.auth.service;

import com.novaops.backend.common.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 登录失败计数与账号锁定（按用户名维度，内存实现）。
 * 目标是拖慢在线爆破：连续失败达到阈值后短时锁定，成功登录即清零。
 * 内存实现意味着重启后清零——对演示规模可接受；如需持久化可换 Redis。
 */
@Component
public class LoginFailureGuard {

  static final int MAX_FAILURES = 5;
  static final Duration LOCK_DURATION = Duration.ofMinutes(10);
  private static final int MAX_TRACKED_USERS = 10_000;

  private final Clock clock;
  private final Map<String, Window> failures = new ConcurrentHashMap<>();

  private record Window(int count, Instant lockedUntil) {
  }

  public LoginFailureGuard() {
    this(Clock.systemUTC());
  }

  LoginFailureGuard(Clock clock) {
    this.clock = clock;
  }

  public void assertNotLocked(String username) {
    Window window = failures.get(username);
    if (window == null || window.lockedUntil() == null) {
      return;
    }
    if (window.lockedUntil().isAfter(clock.instant())) {
      throw new BusinessException(403, "失败次数过多，账号已临时锁定，请 10 分钟后再试");
    }
    failures.remove(username);
  }

  public void recordFailure(String username) {
    Window current = failures.get(username);
    int count = (current == null || current.lockedUntil() != null) ? 1 : current.count() + 1;
    Instant lockedUntil = count >= MAX_FAILURES ? clock.instant().plus(LOCK_DURATION) : null;
    failures.put(username, new Window(count, lockedUntil));
    evictExpiredIfNeeded();
  }

  public void reset(String username) {
    failures.remove(username);
  }

  // 防止伪造大量用户名撑爆内存：超过容量时顺带清理已解锁的过期条目
  private void evictExpiredIfNeeded() {
    if (failures.size() <= MAX_TRACKED_USERS) {
      return;
    }
    Instant now = Instant.now();
    Iterator<Map.Entry<String, Window>> iterator = failures.entrySet().iterator();
    while (iterator.hasNext()) {
      Window window = iterator.next().getValue();
      if (window.lockedUntil() == null || window.lockedUntil().isBefore(now)) {
        iterator.remove();
      }
    }
  }
}
