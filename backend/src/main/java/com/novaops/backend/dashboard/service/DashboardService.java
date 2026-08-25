package com.novaops.backend.dashboard.service;

import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.dashboard.dto.DashboardMetricsResponse;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DashboardService {

  private final JdbcTemplate jdbcTemplate;
  private final AuthService authService;

  public DashboardService(JdbcTemplate jdbcTemplate, AuthService authService) {
    this.jdbcTemplate = jdbcTemplate;
    this.authService = authService;
  }

  public DashboardMetricsResponse metrics(CurrentSession session, String startDate, String endDate) {
    authService.requirePermission(session, "dashboard:view");
    LocalDateTime end = parseDateTime(endDate, LocalDate.now().atTime(23, 59, 59));
    LocalDateTime start = parseDateTime(startDate, end.toLocalDate().minusDays(6).atStartOfDay());
    if (end.isBefore(start)) throw new BusinessException(400, "结束时间不能早于开始时间");
    if (start.toLocalDate().plusDays(366).isBefore(end.toLocalDate())) {
      throw new BusinessException(400, "统计范围不能超过 366 天");
    }

    String tenantId = session.getTenantId();
    Object[] rangeArgs = {tenantId, Timestamp.valueOf(start), Timestamp.valueOf(end)};
    Map<String, Object> overviewRow = jdbcTemplate.queryForMap("""
        select count(*) ticket_total,
               coalesce(sum(status = 'done'), 0) done_total,
               coalesce(avg(case when status = 'done' then timestampdiff(second, created_at, updated_at) / 3600 end), 0) avg_hours,
               coalesce(100 * sum(case when status = 'done' and (due_date is null or updated_at <= due_date) then 1 else 0 end)
                 / nullif(sum(status = 'done'), 0), 0) sla_rate
        from biz_ticket
        where tenant_id = ? and created_at between ? and ?
        """, rangeArgs);

    long total = number(overviewRow.get("ticket_total")).longValue();
    long done = number(overviewRow.get("done_total")).longValue();
    double doneRate = total == 0 ? 0 : round(done * 100.0 / total);
    var overview = new DashboardMetricsResponse.Overview(
        total,
        doneRate,
        round(number(overviewRow.get("avg_hours")).doubleValue()),
        round(number(overviewRow.get("sla_rate")).doubleValue()));

    Map<LocalDate, int[]> trendByDay = new LinkedHashMap<>();
    jdbcTemplate.query("""
        select date(created_at) day,
               count(*) created_count,
               sum(status = 'done') closed_count
        from biz_ticket
        where tenant_id = ? and created_at between ? and ?
        group by date(created_at)
        order by day
        """, (RowCallbackHandler) rs -> trendByDay.put(rs.getDate("day").toLocalDate(),
        new int[] {rs.getInt("created_count"), rs.getInt("closed_count")}), rangeArgs);

    List<String> dates = new ArrayList<>();
    List<Integer> created = new ArrayList<>();
    List<Integer> closed = new ArrayList<>();
    for (LocalDate day = start.toLocalDate(); !day.isAfter(end.toLocalDate()); day = day.plusDays(1)) {
      int[] values = trendByDay.getOrDefault(day, new int[] {0, 0});
      dates.add(String.format("%02d-%02d", day.getMonthValue(), day.getDayOfMonth()));
      created.add(values[0]);
      closed.add(values[1]);
    }

    List<DashboardMetricsResponse.CategoryItem> categories = jdbcTemplate.query("""
        select status, count(*) total
        from biz_ticket
        where tenant_id = ? and created_at between ? and ?
        group by status
        order by field(status, 'pending', 'processing', 'review', 'done')
        """, (rs, rowNum) -> new DashboardMetricsResponse.CategoryItem(
        statusLabel(rs.getString("status")), rs.getLong("total")), rangeArgs);

    List<DashboardMetricsResponse.DurationItem> durations = jdbcTemplate.query("""
        select priority, coalesce(avg(timestampdiff(second, created_at, updated_at) / 3600), 0) hours
        from biz_ticket
        where tenant_id = ? and created_at between ? and ?
        group by priority
        order by field(priority, 'urgent', 'high', 'medium', 'low')
        """, (rs, rowNum) -> new DashboardMetricsResponse.DurationItem(
        priorityLabel(rs.getString("priority")), round(rs.getDouble("hours"))), rangeArgs);

    return new DashboardMetricsResponse(
        new DashboardMetricsResponse.Range(start.toString(), end.toString()),
        overview,
        new DashboardMetricsResponse.Trend(dates, created, closed),
        categories,
        durations);
  }

  private LocalDateTime parseDateTime(String raw, LocalDateTime fallback) {
    if (!StringUtils.hasText(raw)) return fallback;
    try {
      return OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    } catch (DateTimeParseException ignored) {
      try {
        return LocalDateTime.parse(raw);
      } catch (DateTimeParseException exception) {
        throw new BusinessException(400, "日期格式不正确");
      }
    }
  }

  private Number number(Object value) {
    if (value instanceof Number number) return number;
    return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
  }

  private double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  private String statusLabel(String status) {
    return switch (status) {
      case "pending" -> "待处理";
      case "processing" -> "处理中";
      case "review" -> "待复核";
      case "done" -> "已完成";
      default -> status;
    };
  }

  private String priorityLabel(String priority) {
    return switch (priority) {
      case "urgent" -> "紧急";
      case "high" -> "高优先级";
      case "medium" -> "中优先级";
      case "low" -> "低优先级";
      default -> priority;
    };
  }
}
