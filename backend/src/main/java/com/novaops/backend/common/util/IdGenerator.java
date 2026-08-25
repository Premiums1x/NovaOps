package com.novaops.backend.common.util;

import java.util.Locale;
import java.util.UUID;

public final class IdGenerator {

  private IdGenerator() {
  }

  public static String randomId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }

  /**
   * 工单主键是全局唯一的，前缀只用于展示租户来源，唯一性由随机段保证。
   * 不能按“租户前缀 + count 序号”生成：并发创建会撞号，第三个租户起也会互相冲突。
   */
  public static String ticketId(String tenantId) {
    String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    return tenantPrefix(tenantId) + "-TICKET-" + random;
  }

  /** 资产业务编号：租户前缀 + 随机段，保证全局唯一且可读。 */
  public static String assetNo(String tenantId) {
    String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    return tenantPrefix(tenantId) + "-ASSET-" + random;
  }

  private static String tenantPrefix(String tenantId) {
    if ("tenant-a".equals(tenantId)) {
      return "A";
    }
    if ("tenant-b".equals(tenantId)) {
      return "B";
    }
    // 其他租户取 id 尾段的大写字母数字作为前缀，取不到时退化为 T
    String tail = tenantId == null ? "" : tenantId.substring(tenantId.lastIndexOf('-') + 1).toUpperCase(Locale.ROOT);
    tail = tail.replaceAll("[^A-Z0-9]", "");
    return tail.isEmpty() ? "T" : (tail.length() > 8 ? tail.substring(0, 8) : tail);
  }
}
