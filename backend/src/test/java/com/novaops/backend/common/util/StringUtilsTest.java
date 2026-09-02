package com.novaops.backend.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StringUtilsTest {

  private static boolean endsWithLoneHighSurrogate(String text) {
    return !text.isEmpty()
        && Character.isHighSurrogate(text.charAt(text.length() - 1));
  }

  @Test
  void truncatesAsciiTextByCharCount() {
    assertEquals("abc", StringUtils.truncateByCodePoints("abcdef", 3));
  }

  @Test
  void returnsOriginalTextWhenWithinLimit() {
    String text = "abc";
    assertEquals(text, StringUtils.truncateByCodePoints(text, 3));
    assertEquals(text, StringUtils.truncateByCodePoints(text, 100));
  }

  @Test
  void neverSplitsSurrogatePairs() {
    // "a😀bc"：😀 是一个 4 字节的代理对（2 个 UTF-16 char）
    String text = "a😀bc";

    // 截断点恰好落在 😀 代理对中间（保留 2 个 char 时会切出 lone surrogate）
    String truncated = StringUtils.truncateByCodePoints(text, 2);
    assertEquals("a😀", truncated);
    assertFalse(endsWithLoneHighSurrogate(truncated));

    // 截断到 3 个码点：a + 😀 + b，切点安全落在码点边界
    String truncated3 = StringUtils.truncateByCodePoints(text, 3);
    assertEquals("a😀b", truncated3);
    assertFalse(endsWithLoneHighSurrogate(truncated3));
  }

  @Test
  void keepsEntireEmojiWhenLimitReachesPairEnd() {
    String text = "😀😀ok";
    // max=2 个码点 = 完整保留两个 emoji（4 个 char）
    assertEquals("😀😀", StringUtils.truncateByCodePoints(text, 2));
    assertFalse(endsWithLoneHighSurrogate(StringUtils.truncateByCodePoints(text, 2)));
  }

  @Test
  void handlesEdgeInputs() {
    assertEquals("", StringUtils.truncateByCodePoints(null, 5));
    assertEquals("", StringUtils.truncateByCodePoints("abc", 0));
    assertEquals("", StringUtils.truncateByCodePoints("abc", -1));
    assertEquals("", StringUtils.truncateByCodePoints("", 5));
    assertTrue(StringUtils.truncateByCodePoints("😀", 5).length() == 2, "不超限时原样返回");
  }
}
