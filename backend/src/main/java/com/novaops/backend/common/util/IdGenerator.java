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
   * 工单主键是全局唯一的，前缀 A 沿用单租户化前的展示格式，唯一性由随机段保证。
   * 不能按“前缀 + count 序号”生成：并发创建会撞号。
   */
  public static String ticketId() {
    String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    return "A-TICKET-" + random;
  }

  /** 资产业务编号：固定前缀 + 随机段，保证全局唯一且可读。 */
  public static String assetNo() {
    String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    return "A-ASSET-" + random;
  }
}
