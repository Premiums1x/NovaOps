package com.novaops.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novaops.backend.auth.dto.LoginRequest;
import com.novaops.backend.auth.dto.RefreshTokenRequest;
import com.novaops.backend.auth.dto.RegisterRequest;
import com.novaops.backend.auth.dto.RoleResponse;
import com.novaops.backend.auth.dto.UserOptionResponse;
import com.novaops.backend.auth.controller.AuthController;
import com.novaops.backend.auth.mail.EmailSender;
import com.novaops.backend.auth.mapper.AuthMapper;
import com.novaops.backend.auth.model.RefreshTokenRecord;
import com.novaops.backend.auth.model.UserRecord;
import com.novaops.backend.common.config.SecurityProperties;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.security.JwtService;
import com.novaops.backend.common.security.RequirePermission;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 登录、注册与权限校验的安全逻辑单元测试：不依赖数据库，聚焦单租户登录收紧、
 * 注册走成员身份 + 邮箱验证、权限码判定与 refresh token 轮换。
 */
class AuthServiceTest {

  private static final String HASHED = new BCryptPasswordEncoder().encode("123456");

  private AuthMapper authMapper;
  private EmailSender emailSender;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    authMapper = mock(AuthMapper.class);
    emailSender = mock(EmailSender.class);
    SecurityProperties properties = new SecurityProperties();
    properties.setJwtSecret("unit-test-secret-0123456789abcdef0123456789");
    properties.setAccessTokenExpireSeconds(1800);
    properties.setRefreshTokenExpireDays(7);
    authService = new AuthService(authMapper, new BCryptPasswordEncoder(), new JwtService(properties), new LoginFailureGuard(), emailSender);
  }

  @Test
  void loginRejectsWrongPassword() {
    when(authMapper.findUserByUsername("staff")).thenReturn(user("role-staff", true));
    LoginRequest request = loginRequest("staff");
    request.setPassword("wrong-password");

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("账号或密码错误");
  }

  @Test
  void loginRejectsDisabledAccount() {
    when(authMapper.findUserByUsername("staff")).thenReturn(user("role-staff", false));

    assertThatThrownBy(() -> authService.login(loginRequest("staff")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("未激活");
  }

  @Test
  void loginRejectsUnknownUserWithoutSelfRegistration() {
    when(authMapper.findUserByUsername("intruder")).thenReturn(null);

    assertThatThrownBy(() -> authService.login(loginRequest("intruder")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("账号或密码错误");
    verify(authMapper, never()).insertUser(any(UserRecord.class));
  }

  @Test
  void registerCreatesMemberUserAndSendsVerification() {
    when(authMapper.findUserByUsername("newbie")).thenReturn(null);
    when(authMapper.findUserByEmail("newbie@example.com")).thenReturn(null);
    when(authMapper.findRoleById("role-member")).thenReturn(role("role-member", "member"));

    authService.register(registerRequest());

    verify(authMapper).insertUser(any(UserRecord.class));
    verify(authMapper).insertEmailVerification(anyString(), anyString(), eq("register"), any(LocalDateTime.class));
    verify(emailSender).sendVerificationEmail(eq("newbie@example.com"), anyString());
  }

  @Test
  void registerRejectsDuplicateUsername() {
    when(authMapper.findUserByUsername("newbie")).thenReturn(user("role-member", false));

    assertThatThrownBy(() -> authService.register(registerRequest()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("账号已存在");
  }

  @Test
  void requirePermissionDeniesMissingCode() {
    when(authMapper.listPermissions("u-staff")).thenReturn(List.of("ticket:view"));

    assertThatThrownBy(() -> authService.requirePermission(session(), "ticket:close"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("无权限");
  }

  @Test
  void requirePermissionAllowsGrantedCode() {
    when(authMapper.listPermissions("u-staff")).thenReturn(List.of("ticket:view", "ticket:close"));

    authService.requirePermission(session(), "ticket:close");
  }

  @Test
  void listUserOptionsReturnsOnlyMapperProvidedEnabledUsers() {
    UserOptionResponse staff = new UserOptionResponse("u-staff", "staff", "Support Staff");
    when(authMapper.listEnabledUserOptions()).thenReturn(List.of(staff));

    assertThat(authService.listUserOptions()).containsExactly(staff);
    verify(authMapper).listEnabledUserOptions();
  }

  @Test
  void userOptionsEndpointRequiresAssetClaimPermission() throws NoSuchMethodException {
    RequirePermission permission = AuthController.class
        .getMethod("userOptions")
        .getAnnotation(RequirePermission.class);

    assertThat(permission).isNotNull();
    assertThat(permission.value()).isEqualTo("asset:claim");
  }

  @Test
  void loginLocksAccountAfterRepeatedPasswordFailures() {
    UserRecord user = user("role-staff", true);
    when(authMapper.findUserByUsername("staff")).thenReturn(user);
    LoginRequest wrongPassword = loginRequest("staff");
    wrongPassword.setPassword("wrong");

    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> authService.login(wrongPassword)).hasMessageContaining("账号或密码错误");
    }
    // 第 6 次即使密码正确也被锁定拦截
    assertThatThrownBy(() -> authService.login(loginRequest("staff")))
        .hasMessageContaining("临时锁定");
  }

  @Test
  void refreshRotatesTokenAndRevokesOldOne() {
    RefreshTokenRecord record = refreshToken("rt-old", false);
    when(authMapper.findRefreshToken("rt-old")).thenReturn(record);
    when(authMapper.findUserById("u-staff")).thenReturn(user("role-staff", true));

    RefreshTokenRequest request = new RefreshTokenRequest();
    request.setRefreshToken("rt-old");
    var response = authService.refresh(request);

    assertThat(response.getRefreshToken()).isNotBlank().isNotEqualTo("rt-old");
    verify(authMapper).revokeRefreshToken("rt-old");
    verify(authMapper).insertRefreshToken(
        anyString(),
        eq("u-staff"),
        any(LocalDateTime.class));
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

  private RefreshTokenRecord refreshToken(String token, boolean revoked) {
    RefreshTokenRecord record = new RefreshTokenRecord();
    record.setToken(token);
    record.setUserId("u-staff");
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

  private CurrentSession session() {
    return new CurrentSession("u-staff", "staff", "Staff", "tenant-a");
  }

  private LoginRequest loginRequest(String username) {
    LoginRequest request = new LoginRequest();
    request.setUsername(username);
    request.setPassword("123456");
    return request;
  }

  private RegisterRequest registerRequest() {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("newbie");
    request.setEmail("newbie@example.com");
    request.setPassword("strong-pass-123");
    return request;
  }
}
