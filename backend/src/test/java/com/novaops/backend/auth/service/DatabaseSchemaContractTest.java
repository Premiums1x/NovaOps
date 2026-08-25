package com.novaops.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class DatabaseSchemaContractTest {

  @Test
  void freshSchemaContainsPlatformAdminFlagAndInvitationTable() throws IOException {
    String sql = read("sql/novaops_init.sql").toLowerCase(Locale.ROOT);

    assertThat(sql).contains("platform_admin tinyint not null default 0");
    assertThat(sql).contains("create table sys_invitation");
    assertThat(sql).contains(
        "token_hash char(64) not null unique",
        "tenant_id varchar(64) not null",
        "role_id varchar(64) not null",
        "created_by varchar(64) not null",
        "expires_at datetime not null",
        "used_at datetime null",
        "updated_at datetime not null default current_timestamp");
    assertThat(sql).contains("'u-admin', 'admin'").contains("'role-admin', 1, 1, 0");
  }

  @Test
  void deployedDatabaseMigrationIsNonDestructive() throws IOException {
    String sql = read("sql/tenant_invitation_migration.sql").toLowerCase(Locale.ROOT);

    assertThat(sql).contains("alter table sys_user add column platform_admin");
    assertThat(sql).contains("update sys_user set platform_admin = 1");
    assertThat(sql).contains("create table if not exists sys_invitation");
    assertThat(sql).doesNotContain("drop table", "truncate table", "delete from");
  }

  @Test
  void deployedDatabaseMigrationCanResumeAfterColumnWasAdded() throws IOException {
    String sql = read("sql/tenant_invitation_migration.sql").toLowerCase(Locale.ROOT);

    assertThat(sql).contains("information_schema.columns");
    assertThat(sql).contains("prepare platform_admin_migration");
  }

  private String read(String relativePath) throws IOException {
    return Files.readString(Path.of(relativePath));
  }
}
