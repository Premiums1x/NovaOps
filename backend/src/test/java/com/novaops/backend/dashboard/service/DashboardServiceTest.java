package com.novaops.backend.dashboard.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DashboardServiceTest {

  private final AuthService authService = mock(AuthService.class);
  private final DashboardService service = new DashboardService(mock(JdbcTemplate.class), authService);
  private final CurrentSession session = new CurrentSession("user-1", "admin", "System Admin", "tenant-a");

  @Test
  void rejectsAnInvalidDateRangeBeforeQueryingTheDatabase() {
    assertThatThrownBy(() -> service.metrics(session, "2026-04-30T00:00:00Z", "2026-04-01T00:00:00Z"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("结束时间");

    verify(authService).requirePermission(session, "dashboard:view");
  }

  @Test
  void rejectsRangesLongerThanOneYear() {
    assertThatThrownBy(() -> service.metrics(session, "2025-01-01T00:00:00Z", "2026-04-01T00:00:00Z"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("366");
  }
}
