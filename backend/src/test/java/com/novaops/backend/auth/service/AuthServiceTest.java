package com.novaops.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novaops.backend.auth.dto.LoginRequest;
import com.novaops.backend.auth.dto.MenuDataResponse;
import com.novaops.backend.auth.dto.RegisterRequest;
import com.novaops.backend.auth.dto.RefreshTokenRequest;
import com.novaops.backend.auth.dto.RoleResponse;
import com.novaops.backend.auth.dto.UserProfileResponse;
import com.novaops.backend.auth.mapper.AuthMapper;
import com.novaops.backend.auth.model.InvitationRecord;
import com.novaops.backend.auth.model.MenuRecord;
import com.novaops.backend.auth.model.RefreshTokenRecord;
import com.novaops.backend.auth.model.TenantRecord;
import com.novaops.backend.auth.model.UserRecord;
import com.novaops.backend.common.config.SecurityProperties;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.security.JwtService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** 登录与权限校验的安全逻辑单元测试。 */
class AuthServiceTest {

  private static final String HASHED = new BCryptPasswordEncoder().encode("123456");

  private AuthMapper authMapper;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    authMapper = mock(AuthMapper.class);
    SecurityProperties properties = new SecurityProperties();
    properties.setJwtSecret("unit-test-secret-0123456789abcdef0123456789");
    properties.setAccessTokenExpireSeconds(1800);
    properties.setRefreshTokenExpireDays(7);
    authService = new AuthService(authMapper, new BCryptPasswordEncoder(), new JwtService(properties), new LoginFailureGuard());
  }

  @Test
  void loginRejectsWrongPassword() {
    when(authMapper.findUserByUsername("staff")).thenReturn(user("role-staff", true));
    LoginRequest request = loginRequest("staff", "role-staff", "tenant-a");
    request.setPassword("wrong-password");

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("账号或密码错误");
  }

  @Test
  void loginRejectsDisabledAccount() {
    when(authMapper.findUserByUsername("staff")).thenReturn(user("role-staff", false));

    assertThatThrownBy(() -> authService.login(loginRequest("staff", "role-staff", "tenant-a")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("账号已被禁用");
  }

  @Test
  void loginRejectsTenantWithoutMembership() {
    UserRecord user = user("role-staff", true);
    when(authMapper.findUserByUsername("staff")).thenReturn(user);
    when(authMapper.countUserTenant(user.getId(), "tenant-b")).thenReturn(0);

    assertThatThrownBy(() -> authService.login(loginRequest("staff", "role-staff", "tenant-b")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("租户无权限访问");
  }

  @Test
  void loginRejectsRoleMismatch() {
    UserRecord user = user("role-staff", true);
    when(authMapper.findUserByUsername("staff")).thenReturn(user);
    when(authMapper.countUserTenant(user.getId(), "tenant-a")).thenReturn(1);

    assertThatThrownBy(() -> authService.login(loginRequest("staff", "role-guest", "tenant-a")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("身份不匹配");
  }

  @Test
  void loginRejectsUnknownUsernameWithoutCreatingAccount() {
    when(authMapper.findUserByUsername("newbie")).thenReturn(null);

    assertThatThrownBy(() -> authService.login(loginRequest("newbie", "role-staff", "tenant-a")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("账号或密码错误");
    verify(authMapper, never()).insertUser(org.mockito.ArgumentMatchers.any(UserRecord.class));
    verify(authMapper, never()).insertUserTenant(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void requirePermissionDeniesMissingCode() {
    when(authMapper.listPermissions("u-staff", "tenant-a")).thenReturn(List.of("ticket:view"));

    assertThatThrownBy(() -> authService.requirePermission(session(), "ticket:close"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("无权限");
  }

  @Test
  void requirePermissionAllowsGrantedCode() {
    when(authMapper.listPermissions("u-staff", "tenant-a")).thenReturn(List.of("ticket:view", "ticket:close"));

    authService.requirePermission(session(), "ticket:close");
  }

  @Test
  void loginLocksAccountAfterRepeatedPasswordFailures() {
    UserRecord user = user("role-staff", true);
    when(authMapper.findUserByUsername("staff")).thenReturn(user);
    LoginRequest wrongPassword = loginRequest("staff", "role-staff", "tenant-a");
    wrongPassword.setPassword("wrong");

    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> authService.login(wrongPassword)).hasMessageContaining("账号或密码错误");
    }
    // 第 6 次即使密码正确也被锁定拦截
    assertThatThrownBy(() -> authService.login(loginRequest("staff", "role-staff", "tenant-a")))
        .hasMessageContaining("临时锁定");
  }

  @Test
  void refreshRotatesTokenAndRevokesOldOne() {
    RefreshTokenRecord record = refreshToken("rt-old", false);
    when(authMapper.findRefreshToken("rt-old")).thenReturn(record);
    when(authMapper.findUserById("u-staff")).thenReturn(user("role-staff", true));
    when(authMapper.countUserTenant("u-staff", "tenant-a")).thenReturn(1);

    RefreshTokenRequest request = new RefreshTokenRequest();
    request.setRefreshToken("rt-old");
    var response = authService.refresh(request);

    assertThat(response.getRefreshToken()).isNotBlank().isNotEqualTo("rt-old");
    verify(authMapper).revokeRefreshToken("rt-old");
    verify(authMapper).insertRefreshToken(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(LocalDateTime.class));
  }

  @Test
  void refreshRejectsRevokedToken() {
    when(authMapper.findRefreshToken("rt-revoked")).thenReturn(refreshToken("rt-revoked", true));

    RefreshTokenRequest request = new RefreshTokenRequest();
    request.setRefreshToken("rt-revoked");
    assertThatThrownBy(() -> authService.refresh(request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已失效");
  }

  @Test
  void refreshRejectsWhenTenantMembershipRemoved() {
    RefreshTokenRecord record = refreshToken("rt-old", false);
    when(authMapper.findRefreshToken("rt-old")).thenReturn(record);
    when(authMapper.findUserById("u-staff")).thenReturn(user("role-staff", true));
    when(authMapper.countUserTenant("u-staff", "tenant-a")).thenReturn(0);

    RefreshTokenRequest request = new RefreshTokenRequest();
    request.setRefreshToken("rt-old");
    assertThatThrownBy(() -> authService.refresh(request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("租户");
  }

  @Test
  void menuOmitsPagesWithoutTheirRequiredPermission() {
    UserRecord user = user("role-staff", true);
    MenuRecord dashboard = menu("dashboard", "/dashboard", "dashboard:view");
    MenuRecord users = menu("users", "/system/users", "auth:user:manage");
    when(authMapper.findUserById("u-staff")).thenReturn(user);
    when(authMapper.listRolesByUserId("u-staff")).thenReturn(List.of("staff"));
    when(authMapper.listPermissions("u-staff", "tenant-a")).thenReturn(List.of("dashboard:view"));
    when(authMapper.listMenusByScope("staff")).thenReturn(List.of(dashboard, users));

    MenuDataResponse response = authService.menu(session());

    assertThat(response.getMenus()).extracting("path").containsExactly("/dashboard");
  }

  @Test
  void administratorCannotChangeOwnRole() {
    UserRecord administrator = user("role-admin", true);
    when(authMapper.findUserById("u-staff")).thenReturn(administrator);
    when(authMapper.findRoleById("role-admin")).thenReturn(role("role-admin", "admin"));

    assertThatThrownBy(() -> authService.updateUserRole(session(), "u-staff", "role-staff"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不能修改当前登录账号");
    verify(authMapper, never()).updateUserRole(anyString(), anyString());
  }

  @Test
  void meExposesPlatformAdministratorFlagAndOnlyUserMemberships() {
    UserRecord user = user("role-admin", true);
    user.setPlatformAdmin(true);
    TenantRecord tenant = new TenantRecord();
    tenant.setId("tenant-a");
    tenant.setName("Tenant A");
    when(authMapper.findUserById("u-staff")).thenReturn(user);
    when(authMapper.listRolesByUserId("u-staff")).thenReturn(List.of("admin"));
    when(authMapper.listPermissions("u-staff", "tenant-a")).thenReturn(List.of("dashboard:view"));
    when(authMapper.listTenantsByUserId("u-staff")).thenReturn(List.of(tenant));

    UserProfileResponse response = authService.me(session());

    assertThat(response.getPlatformAdmin()).isTrue();
    assertThat(response.getTenants()).extracting("id").containsExactly("tenant-a");
  }

  @Test
  void invitationRegistrationCreatesMembershipConsumesTokenAndReturnsLogin() {
    InvitationRecord invitation = invitation("role-staff", LocalDateTime.now().plusDays(1), null);
    when(authMapper.findInvitationByTokenHashForUpdate(SystemService.sha256("raw-invitation-token"))).thenReturn(invitation);
    when(authMapper.findRoleById("role-staff")).thenReturn(role("role-staff", "staff"));
    when(authMapper.findUserByUsername("newbie")).thenReturn(null);
    when(authMapper.consumeInvitation(org.mockito.ArgumentMatchers.eq("inv-1"), org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(1);

    var response = authService.register(registerRequest());

    assertThat(response.getTenantId()).isEqualTo("tenant-a");
    assertThat(response.getAccessToken()).isNotBlank();
    ArgumentCaptor<UserRecord> userCaptor = ArgumentCaptor.forClass(UserRecord.class);
    verify(authMapper).insertUser(userCaptor.capture());
    assertThat(userCaptor.getValue().getUsername()).isEqualTo("newbie");
    assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("New User");
    assertThat(userCaptor.getValue().getRoleId()).isEqualTo("role-staff");
    assertThat(new BCryptPasswordEncoder().matches("strong-password", userCaptor.getValue().getPasswordHash())).isTrue();
    verify(authMapper).insertUserTenant(userCaptor.getValue().getId(), "tenant-a");
    verify(authMapper).consumeInvitation(org.mockito.ArgumentMatchers.eq("inv-1"), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
  }

  @Test
  void invitationRegistrationRejectsExpiredInvitation() {
    when(authMapper.findInvitationByTokenHashForUpdate(SystemService.sha256("raw-invitation-token")))
        .thenReturn(invitation("role-staff", LocalDateTime.now().minusMinutes(1), null));

    assertThatThrownBy(() -> authService.register(registerRequest()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已过期");
    verify(authMapper, never()).insertUser(org.mockito.ArgumentMatchers.any(UserRecord.class));
  }

  @Test
  void invitationRegistrationRejectsReplay() {
    when(authMapper.findInvitationByTokenHashForUpdate(SystemService.sha256("raw-invitation-token")))
        .thenReturn(invitation("role-staff", LocalDateTime.now().plusDays(1), LocalDateTime.now()));

    assertThatThrownBy(() -> authService.register(registerRequest()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已使用");
    verify(authMapper, never()).insertUser(org.mockito.ArgumentMatchers.any(UserRecord.class));
  }

  @Test
  void invitationRegistrationRejectsInvalidRoleStoredInInvitation() {
    when(authMapper.findInvitationByTokenHashForUpdate(SystemService.sha256("raw-invitation-token")))
        .thenReturn(invitation("role-admin", LocalDateTime.now().plusDays(1), null));
    when(authMapper.findRoleById("role-admin")).thenReturn(role("role-admin", "admin"));

    assertThatThrownBy(() -> authService.register(registerRequest()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("身份无效");
    verify(authMapper, never()).insertUser(org.mockito.ArgumentMatchers.any(UserRecord.class));
  }

  @Test
  void invitationRegistrationFailsWhenAtomicConsumptionLosesRace() {
    InvitationRecord invitation = invitation("role-guest", LocalDateTime.now().plusDays(1), null);
    when(authMapper.findInvitationByTokenHashForUpdate(SystemService.sha256("raw-invitation-token"))).thenReturn(invitation);
    when(authMapper.findRoleById("role-guest")).thenReturn(role("role-guest", "guest"));
    when(authMapper.findUserByUsername("newbie")).thenReturn(null);
    when(authMapper.consumeInvitation(org.mockito.ArgumentMatchers.eq("inv-1"), org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(0);

    assertThatThrownBy(() -> authService.register(registerRequest()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已使用或已过期");
  }

  @Test
  void invitationRegistrationTranslatesConcurrentDuplicateUsernameWithoutConsumingInvitation() {
    InvitationRecord invitation = invitation("role-staff", LocalDateTime.now().plusDays(1), null);
    when(authMapper.findInvitationByTokenHashForUpdate(SystemService.sha256("raw-invitation-token"))).thenReturn(invitation);
    when(authMapper.findRoleById("role-staff")).thenReturn(role("role-staff", "staff"));
    when(authMapper.findUserByUsername("newbie")).thenReturn(null);
    doThrow(new DuplicateKeyException("duplicate username"))
        .when(authMapper).insertUser(any(UserRecord.class));

    assertThatThrownBy(() -> authService.register(registerRequest()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("用户名已存在");

    verify(authMapper, never()).insertUserTenant(anyString(), anyString());
    verify(authMapper, never()).consumeInvitation(anyString(), any(LocalDateTime.class));
    verify(authMapper, never()).insertRefreshToken(anyString(), anyString(), anyString(), any(LocalDateTime.class));
  }

  @Test
  void invitationRegistrationRunsInTransaction() throws Exception {
    assertThat(AuthService.class.getMethod("register", RegisterRequest.class).getAnnotation(Transactional.class)).isNotNull();
  }

  private RefreshTokenRecord refreshToken(String token, boolean revoked) {
    RefreshTokenRecord record = new RefreshTokenRecord();
    record.setToken(token);
    record.setUserId("u-staff");
    record.setTenantId("tenant-a");
    record.setExpiresAt(LocalDateTime.now().plusDays(1));
    record.setRevoked(revoked);
    return record;
  }

  private InvitationRecord invitation(String roleId, LocalDateTime expiresAt, LocalDateTime usedAt) {
    InvitationRecord invitation = new InvitationRecord();
    invitation.setId("inv-1");
    invitation.setTokenHash(SystemService.sha256("raw-invitation-token"));
    invitation.setTenantId("tenant-a");
    invitation.setRoleId(roleId);
    invitation.setCreatedBy("u-admin");
    invitation.setExpiresAt(expiresAt);
    invitation.setUsedAt(usedAt);
    invitation.setCreatedAt(LocalDateTime.now().minusHours(1));
    invitation.setUpdatedAt(LocalDateTime.now().minusHours(1));
    return invitation;
  }

  private RegisterRequest registerRequest() {
    RegisterRequest request = new RegisterRequest();
    request.setInvitationToken("raw-invitation-token");
    request.setUsername("newbie");
    request.setDisplayName("New User");
    request.setPassword("strong-password");
    return request;
  }

  private RoleResponse role(String id, String code) {
    RoleResponse role = new RoleResponse();
    role.setId(id);
    role.setCode(code);
    return role;
  }

  private MenuRecord menu(String id, String path, String permissionCode) {
    MenuRecord menu = new MenuRecord();
    menu.setId(id);
    menu.setTitle(id);
    menu.setName(id);
    menu.setPath(path);
    menu.setComponent("DashboardView");
    menu.setPermissionCode(permissionCode);
    menu.setKeepAlive(true);
    return menu;
  }

  private UserRecord user(String roleId, boolean enabled) {
    UserRecord user = new UserRecord();
    user.setId("u-staff");
    user.setUsername("staff");
    user.setPasswordHash(HASHED);
    user.setDisplayName("Staff");
    user.setRoleId(roleId);
    user.setEnabled(enabled);
    return user;
  }

  private CurrentSession session() {
    return new CurrentSession("u-staff", "staff", "Staff", "tenant-a");
  }

  private LoginRequest loginRequest(String username, String roleId, String tenantId) {
    LoginRequest request = new LoginRequest();
    request.setUsername(username);
    request.setPassword("123456");
    request.setRoleId(roleId);
    request.setTenantId(tenantId);
    return request;
  }
}
