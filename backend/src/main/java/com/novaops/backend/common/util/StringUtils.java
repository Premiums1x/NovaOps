package com.novaops.backend.common.util;

/** 字符串工具。 */
public final class StringUtils {

  private StringUtils() {
  }

  /**
   * 按 Unicode 码点截断到最多 max 个字符，避免 substring 按 UTF-16 char
   * 切在代理对中间产生 lone surrogate（emoji 等显示为乱码）。
   * 文本不超限时原样返回；max 不为正或文本为 null 时返回空串。
   */
  public static String truncateByCodePoints(String text, int max) {
    if (text == null || max <= 0) {
      return "";
    }
    if (max >= text.codePointCount(0, text.length())) {
      return text;
    }
    return text.substring(0, text.offsetByCodePoints(0, max));
  }
}
