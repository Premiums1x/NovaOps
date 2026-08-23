package com.novaops.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdGeneratorTest {

  @Test
  void keepsLegacyTenantPrefixes() {
    assertThat(IdGenerator.ticketId("tenant-a")).startsWith("A-TICKET-");
    assertThat(IdGenerator.ticketId("tenant-b")).startsWith("B-TICKET-");
  }

  @Test
  void unknownTenantGetsSanitizedPrefix() {
    assertThat(IdGenerator.ticketId("tenant-acme")).startsWith("ACME-TICKET-");
    assertThat(IdGenerator.ticketId(null)).startsWith("T-TICKET-");
    assertThat(IdGenerator.ticketId("")).startsWith("T-TICKET-");
  }

  @Test
  void ticketIdsAreGloballyUniqueAcrossTenants() {
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < 5000; i++) {
      assertThat(ids.add(IdGenerator.ticketId("tenant-a"))).isTrue();
      assertThat(ids.add(IdGenerator.ticketId("tenant-b"))).isTrue();
      assertThat(ids.add(IdGenerator.ticketId("tenant-c"))).isTrue();
    }
  }
}
