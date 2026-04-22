package com.novaops.backend.common.util;

import java.util.UUID;

public final class IdGenerator {

  private IdGenerator() {
  }

  public static String randomId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }

  public static String ticketId(String tenantId, long sequence) {
    String prefix = "tenant-a".equals(tenantId) ? "A" : "B";
    return prefix + "-TICKET-" + String.format("%04d", sequence);
  }
}
