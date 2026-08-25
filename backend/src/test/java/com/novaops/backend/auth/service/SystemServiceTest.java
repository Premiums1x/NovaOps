package com.novaops.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novaops.backend.auth.dto.CreateInvitationRequest;
import com.novaops.backend.auth.dto.CreateTenantRequest;
import com.novaops.backend.auth.dto.RoleResponse;
import com.novaops.backend.auth.mapper.AuthMapper;
import com.novaops.backend.auth.model.InvitationRecord;
import com.novaops.backend.auth.model.TenantRecord;
import com.novaops.backend.auth.model.UserRecord;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class SystemServiceTest {

  private static final Clock FIXED_CLOCK = Clock.fixed(
      Instant.parse("2026-08-24T01:00:00Z"), ZoneId.of("Asia/Shanghai"));

  private AuthMapper authMapper;
  private SystemService systemService;

  @BeforeEach
  void setUp() {
    authMapper = mock(AuthMapper.class);
    systemService = new SystemService(authMapper, FIXED_CLOCK);
  }

  @Test
  void tenantListingRejectsRoleAdminWithoutPlatformAdministratorFlag() {
    when(authMapper.findUserById("u-admin")).thenReturn(user(false));

    assertThatThrownBy(() -> systemService.listTenants(session()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("平台管理员");
  }

  @Test
  void tenantCreationRejectsPlatformFlagWithoutRoleAdminBinding() {
    UserRecord staffPlatformAdmin = user(true);
    staffPlatformAdmin.setRoleId("role-staff");
    when(authMapper.findUserById("u-admin")).thenReturn(staffPlatformAdmin);
    CreateTenantRequest request = new CreateTenantRequest();
    request.setCode("acme");
    request.setName("Acme Corp");

    assertThatThrownBy(() -> systemService.createTenant(session(), request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("role-admin");
    verify(authMapper, never()).insertTenant(any(TenantRecord.class));
  }

  @Test
  void platformAdministratorCanListAllTenants() {
    allowPlatformAdmin();
    TenantRecord tenant = tenant("acme", "Acme Corp");
    when(authMapper.listAllTenants()).thenReturn(List.of(tenant));

    var result = systemService.listTenants(session());

    assertThat(result).singleElement().satisfies(item -> {
      assertThat(item.getCode()).isEqualTo("acme");
      assertThat(item.getName()).isEqualTo("Acme Corp");
    });
  }

  @Test
  void createTenantClonesTenantAPermissionsAndBindsCreator() {
    allowPlatformAdmin();
    CreateTenantRequest request = new CreateTenantRequest();
    request.setCode("acme");
    request.setName("Acme Corp");

    var result = systemService.createTenant(session(), request);

    ArgumentCaptor<TenantRecord> tenantCaptor = ArgumentCaptor.forClass(TenantRecord.class);
    verify(authMapper).insertTenant(tenantCaptor.capture());
    assertThat(tenantCaptor.getValue().getId()).isEqualTo("acme");
    assertThat(tenantCaptor.getValue().getName()).isEqualTo("Acme Corp");
    verify(authMapper).cloneTenantRolePermissions("tenant-a", "acme");
    verify(authMapper).insertUserTenant("u-admin", "acme");
    assertThat(result.getCode()).isEqualTo("acme");
  }

  @Test
  void createTenantRejectsDuplicateStableCode() {
    allowPlatformAdmin();
    when(authMapper.findTenantById("acme")).thenReturn(tenant("acme", "Existing"));
    CreateTenantRequest request = new CreateTenantRequest();
    request.setCode("acme");
    request.setName("Acme Corp");

    assertThatThrownBy(() -> systemService.createTenant(session(), request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已存在");
    verify(authMapper, never()).insertTenant(any(TenantRecord.class));
  }

  @Test
  void createTenantTranslatesConcurrentDuplicateCodeRace() {
    allowPlatformAdmin();
    CreateTenantRequest request = new CreateTenantRequest();
    request.setCode("acme");
    request.setName("Acme Corp");
    doThrow(new DuplicateKeyException("duplicate tenant code"))
        .when(authMapper).insertTenant(any(TenantRecord.class));

    assertThatThrownBy(() -> systemService.createTenant(session(), request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已存在");
    verify(authMapper, never()).cloneTenantRolePermissions(any(), any());
    verify(authMapper, never()).insertUserTenant(any(), any());
  }

  @Test
  void invitationCreationRejectsRolesOtherThanStaffOrGuest() {
    allowPlatformAdmin();
    CreateInvitationRequest request = invitationRequest("admin");

    assertThatThrownBy(() -> systemService.createInvitation(session(), request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("staff 或 guest");
    verify(authMapper, never()).insertInvitation(any(InvitationRecord.class));
  }

  @Test
  void invitationCreationReturnsRawTokenButPersistsOnlySha256Hash() {
    allowPlatformAdmin();
    when(authMapper.findTenantById("tenant-a")).thenReturn(tenant("tenant-a", "Tenant A"));
    when(authMapper.findRoleByCode("staff")).thenReturn(role("role-staff", "staff"));

    var result = systemService.createInvitation(session(), invitationRequest("staff"));

    ArgumentCaptor<InvitationRecord> invitationCaptor = ArgumentCaptor.forClass(InvitationRecord.class);
    verify(authMapper).insertInvitation(invitationCaptor.capture());
    InvitationRecord stored = invitationCaptor.getValue();
    assertThat(result.getToken()).isNotBlank();
    assertThat(stored.getTokenHash()).hasSize(64).doesNotContain(result.getToken());
    assertThat(stored.getRoleId()).isEqualTo("role-staff");
    assertThat(stored.getTenantId()).isEqualTo("tenant-a");
    assertThat(stored.getCreatedBy()).isEqualTo("u-admin");
  }

  @Test
  void invitationExpiryIsExactlySevenDaysAfterServerCreationTime() {
    allowPlatformAdmin();
    when(authMapper.findTenantById("tenant-a")).thenReturn(tenant("tenant-a", "Tenant A"));
    when(authMapper.findRoleByCode("staff")).thenReturn(role("role-staff", "staff"));

    systemService.createInvitation(session(), invitationRequest("staff"));

    ArgumentCaptor<InvitationRecord> invitationCaptor = ArgumentCaptor.forClass(InvitationRecord.class);
    verify(authMapper).insertInvitation(invitationCaptor.capture());
    InvitationRecord stored = invitationCaptor.getValue();
    assertThat(stored.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 24, 9, 0));
    assertThat(stored.getExpiresAt()).isEqualTo(stored.getCreatedAt().plusDays(7));
  }

  private CurrentSession session() {
    return new CurrentSession("u-admin", "admin", "Admin", "tenant-a");
  }

  private void allowPlatformAdmin() {
    when(authMapper.findUserById("u-admin")).thenReturn(user(true));
  }

  private TenantRecord tenant(String id, String name) {
    TenantRecord tenant = new TenantRecord();
    tenant.setId(id);
    tenant.setName(name);
    return tenant;
  }

  private RoleResponse role(String id, String code) {
    RoleResponse role = new RoleResponse();
    role.setId(id);
    role.setCode(code);
    return role;
  }

  private CreateInvitationRequest invitationRequest(String roleCode) {
    CreateInvitationRequest request = new CreateInvitationRequest();
    request.setTenantId("tenant-a");
    request.setRoleCode(roleCode);
    return request;
  }

  private UserRecord user(boolean platformAdmin) {
    UserRecord user = new UserRecord();
    user.setId("u-admin");
    user.setUsername("admin");
    user.setDisplayName("Admin");
    user.setRoleId("role-admin");
    user.setEnabled(true);
    user.setPlatformAdmin(platformAdmin);
    return user;
  }
}
