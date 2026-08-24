package com.novaops.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novaops.backend.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DateTimeUtilsTest {

  @Test
  void blankValueReturnsNull() {
    assertThat(DateTimeUtils.parseIsoDateTime(null)).isNull();
    assertThat(DateTimeUtils.parseIsoDateTime("  ")).isNull();
  }

  @Test
  void parsesInstantWithZoneSuffix() {
    LocalDateTime expected = LocalDateTime.ofInstant(
        java.time.Instant.parse("2026-04-20T09:00:00Z"), ZoneId.systemDefault());
    assertThat(DateTimeUtils.parseIsoDateTime("2026-04-20T09:00:00Z")).isEqualTo(expected);
  }

  @Test
  void parsesOffsetDateTime() {
    LocalDateTime expected = LocalDateTime.ofInstant(
        java.time.Instant.parse("2026-04-20T01:00:00Z"), ZoneId.systemDefault());
    assertThat(DateTimeUtils.parseIsoDateTime("2026-04-20T09:00:00+08:00")).isEqualTo(expected);
  }

  @Test
  void parsesLocalDateTimeWithoutZone() {
    assertThat(DateTimeUtils.parseIsoDateTime("2026-04-20T09:00:00"))
        .isEqualTo(LocalDateTime.of(2026, 4, 20, 9, 0, 0));
  }

  @Test
  void rejectsInvalidFormatWithBusiness400() {
    assertThatThrownBy(() -> DateTimeUtils.parseIsoDateTime("2026-04-20"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("日期格式不合法");
    assertThatThrownBy(() -> DateTimeUtils.parseIsoDateTime("not-a-date"))
        .isInstanceOf(BusinessException.class)
        .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
  }
}
