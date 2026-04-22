package com.novaops.backend.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.util.StringUtils;

public final class DateTimeUtils {

  private static final ZoneId ZONE_ID = ZoneId.systemDefault();
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  private DateTimeUtils() {
  }

  public static LocalDateTime parseIsoDateTime(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return LocalDateTime.ofInstant(Instant.parse(value), ZONE_ID);
  }

  public static String toIsoString(LocalDateTime value) {
    if (value == null) {
      return null;
    }
    return value.atZone(ZONE_ID).toOffsetDateTime().format(FORMATTER);
  }
}
