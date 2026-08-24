package com.novaops.backend.common.util;

import com.novaops.backend.common.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.util.StringUtils;

public final class DateTimeUtils {

  private static final ZoneId ZONE_ID = ZoneId.systemDefault();
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  private DateTimeUtils() {
  }

  /**
   * 同时接受带时区的 ISO 格式（2026-04-20T09:00:00Z）与本地格式（2026-04-20T09:00:00）。
   * 非法输入返回 400 业务异常，而不是让 DateTimeParseException 落到兜底的 500。
   */
  public static LocalDateTime parseIsoDateTime(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    String trimmed = value.trim();
    try {
      return LocalDateTime.ofInstant(Instant.parse(trimmed), ZONE_ID);
    } catch (DateTimeParseException ignored) {
      try {
        return LocalDateTime.parse(trimmed);
      } catch (DateTimeParseException ex) {
        throw new BusinessException(400, "日期格式不合法，应为 ISO 格式如 2026-04-20T09:00:00");
      }
    }
  }

  public static String toIsoString(LocalDateTime value) {
    if (value == null) {
      return null;
    }
    return value.atZone(ZONE_ID).toOffsetDateTime().format(FORMATTER);
  }
}
