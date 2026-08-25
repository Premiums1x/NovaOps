package com.novaops.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novaops.backend.auth.dto.LoginRequest;
import com.novaops.backend.auth.dto.RefreshTokenRequest;
import com.novaops.backend.auth.dto.RoleResponse;
import com.novaops.backend.auth.mapper.AuthMapper;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 登录与权限校验的安全逻辑单元测试：不依赖数据库，聚焦自助注册收紧、
 * 租户归属校验、身份匹配与权限码判定。
 */
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
  void selfRegistrationRejectsAdminRole() {
    when(authMapper.findUserByUsername("intruder")).thenReturn(null);
    when(authMapper.findRoleById("role-admin")).thenReturn(role("role-admin", "admin"));

    assertThatThrownBy(() -> authService.login(loginRequest("intruder", "role-admin", "tenant-a")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("管理员账号不支持自助注册");
    verify(authMapper, never()).insertUser(org.mockito.ArgumentMatchers.any(UserRecord.class));
  }

  @Test
  void selfRegistrationRejectsUnknownTenant() {
    when(authMapper.findUserByUsername("intruder")).thenReturn(null);
    when(authMapper.findRoleById("role-staff")).thenReturn(role("role-staff", "staff"));
    when(authMapper.findTenantById("tenant-evil")).thenReturn(null);

    assertThatThrownBy(() -> authService.login(loginRequest("intruder", "role-staff", "tenant-evil")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("租户不存在");
  }

  @Test
  void selfRegistrationCreatesStaffUserWithTenantBinding() {
    when(authMapper.findUserByUsername("newbie")).thenReturn(null);
    when(authMapper.findRoleById("role-staff")).thenReturn(role("role-staff", "staff"));
    when(authMapper.findTenantById("tenant-a")).thenReturn(tenant("tenant-a"));

    var response = authService.login(loginRequest("newbie", "role-staff", "tenant-a"));

    assertThat(response.getAccessToken()).isNotBlank();
    assertThat(response.getTenantId()).isEqualTo("tenant-a");
    verify(authMapper).insertUser(org.mockito.ArgumentMatchers.any(UserRecord.class));
    verify(authMapper).insertUserTenant(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("tenant-a"));
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

  private RefreshTokenRecord refreshToken(String token, boolean revoked) {
    RefreshTokenRecord record = new RefreshTokenRecord();
    record.setToken(token);
    record.setUserId("u-staff");
    record.setTenantId("tenant-a");
    record.setExpiresAt(LocalDateTime.now().plusDays(1));
    record.setRevoked(revoked);
    return record;
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

  private RoleResponse role(String id, String code) {
    RoleResponse role = new RoleResponse();
    role.setId(id);
    role.setCode(code);
    return role;
  }

  private TenantRecord tenant(String id) {
    TenantRecord tenant = new TenantRecord();
    tenant.setId(id);
    tenant.setName("Tenant " + id);
    return tenant;
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
