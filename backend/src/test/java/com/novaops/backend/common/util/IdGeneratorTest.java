package com.novaops.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdGeneratorTest {

  @Test
  void ticketAndAssetNumbersKeepLegacyPrefixFormat() {
    assertThat(IdGenerator.ticketId()).startsWith("A-TICKET-");
    assertThat(IdGenerator.assetNo()).startsWith("A-ASSET-");
  }

  @Test
  void ticketIdsAreGloballyUnique() {
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < 15000; i++) {
      assertThat(ids.add(IdGenerator.ticketId())).isTrue();
    }
  }
}
