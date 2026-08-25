package com.novaops.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.novaops.backend.auth.model.UserRecord;
import com.novaops.backend.auth.model.InvitationRecord;
import com.novaops.backend.auth.model.TenantRecord;
import com.novaops.backend.auth.dto.InvitationResponse;
import com.novaops.backend.auth.mapper.AuthMapper;
import com.novaops.backend.auth.dto.CreateInvitationRequest;
import com.novaops.backend.auth.dto.CreateTenantRequest;
import com.novaops.backend.auth.dto.RegisterRequest;
import com.novaops.backend.common.config.SecurityConfig;
import com.novaops.backend.common.security.CurrentSession;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SystemApiContractTest {

  @Test
  void userRecordExposesPlatformAdministratorFlag() {
    assertThat(Arrays.stream(UserRecord.class.getMethods()).map(method -> method.getName()))
        .contains("getPlatformAdmin", "setPlatformAdmin");
  }

  @Test
  void systemAndInvitationRegistrationTypesExist() {
    assertThatCode(() -> {
      Class.forName("com.novaops.backend.auth.service.SystemService");
      Class.forName("com.novaops.backend.auth.model.InvitationRecord");
      Class.forName("com.novaops.backend.auth.dto.CreateTenantRequest");
      Class.forName("com.novaops.backend.auth.dto.TenantResponse");
      Class.forName("com.novaops.backend.auth.dto.CreateInvitationRequest");
      Class.forName("com.novaops.backend.auth.dto.InvitationResponse");
      Class.forName("com.novaops.backend.auth.dto.CreatedInvitationResponse");
      Class.forName("com.novaops.backend.auth.dto.RegisterRequest");
    }).doesNotThrowAnyException();
  }

  @Test
  void servicesExposeTenantInvitationAndRegistrationOperations() {
    assertThatCode(() -> {
      SystemService.class.getMethod("listTenants", CurrentSession.class);
      SystemService.class.getMethod("createTenant", CurrentSession.class, CreateTenantRequest.class);
      SystemService.class.getMethod("listInvitations", CurrentSession.class);
      SystemService.class.getMethod("createInvitation", CurrentSession.class, CreateInvitationRequest.class);
      AuthService.class.getMethod("register", RegisterRequest.class);
    }).doesNotThrowAnyException();
  }

  @Test
  void mapperExposesTransactionalTenantAndInvitationOperations() {
    assertThatCode(() -> {
      AuthMapper.class.getMethod("listAllTenants");
      AuthMapper.class.getMethod("insertTenant", TenantRecord.class);
      AuthMapper.class.getMethod("cloneTenantRolePermissions", String.class, String.class);
      AuthMapper.class.getMethod("listInvitations");
      AuthMapper.class.getMethod("insertInvitation", InvitationRecord.class);
      AuthMapper.class.getMethod("findRoleByCode", String.class);
      AuthMapper.class.getMethod("findInvitationByTokenHashForUpdate", String.class);
      AuthMapper.class.getMethod("consumeInvitation", String.class, LocalDateTime.class);
    }).doesNotThrowAnyException();
  }

  @Test
  void invitationCreationUsesInjectedClockAndDoesNotAcceptClientExpiry() {
    assertThat(Arrays.stream(CreateInvitationRequest.class.getDeclaredFields()).map(field -> field.getName()))
        .doesNotContain("expiresAt");
    assertThatCode(() -> SystemService.class.getConstructor(AuthMapper.class, Clock.class))
        .doesNotThrowAnyException();
  }

  @Test
  void applicationProvidesClockBeanForServerOwnedExpiry() {
    assertThatCode(() -> assertThat(SecurityConfig.class.getMethod("clock").getReturnType()).isEqualTo(Clock.class))
        .doesNotThrowAnyException();
  }
}
