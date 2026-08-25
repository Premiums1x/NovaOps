package com.novaops.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AuthMapperSqlContractTest {

  @Test
  void userQueriesLoadPlatformAdministratorFlag() throws IOException {
    String xml = mapperXml();

    assertThat(xml).contains("platform_admin");
  }

  @Test
  void tenantCreationSqlSupportsInsertAndPermissionClone() throws IOException {
    String xml = mapperXml();

    assertThat(xml).contains("<insert id=\"insertTenant\">");
    assertThat(xml).contains("<insert id=\"cloneTenantRolePermissions\">");
    assertThat(xml).contains("#{sourceTenantId}", "#{targetTenantId}");
  }

  @Test
  void registrationLocksInvitationAndConsumesItWithCompareAndSet() throws IOException {
    String xml = mapperXml();

    String lockQuery = statement(xml, "<select id=\"findInvitationByTokenHashForUpdate\"", "</select>");
    assertThat(lockQuery).containsIgnoringCase("for update");
    String consumeUpdate = statement(xml, "<update id=\"consumeInvitation\"", "</update>");
    assertThat(consumeUpdate)
        .containsIgnoringCase("used_at is null")
        .contains("expires_at &gt; #{usedAt}");
  }

  @Test
  void invitationListingNeverSelectsRawTokenOrHash() throws IOException {
    String xml = mapperXml();

    String listQuery = statement(xml, "<select id=\"listInvitations\"", "</select>");
    assertThat(listQuery).doesNotContain("token_hash", "token");
  }

  private String mapperXml() throws IOException {
    try (var input = getClass().getResourceAsStream("/mapper/AuthMapper.xml")) {
      if (input == null) throw new IOException("AuthMapper.xml not found");
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private String statement(String xml, String startMarker, String endMarker) {
    int start = xml.indexOf(startMarker);
    assertThat(start).as("statement %s exists", startMarker).isGreaterThanOrEqualTo(0);
    int end = xml.indexOf(endMarker, start);
    assertThat(end).as("statement ending %s exists", endMarker).isGreaterThan(start);
    return xml.substring(start, end + endMarker.length());
  }
}
