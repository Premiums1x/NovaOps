package com.novaops.backend.auth.service;

import com.novaops.backend.auth.dto.AuthTokenResponse;
import com.novaops.backend.auth.dto.LoginRequest;
import com.novaops.backend.auth.dto.LoginResponse;
import com.novaops.backend.auth.dto.MenuDataResponse;
import com.novaops.backend.auth.dto.MenuItemResponse;
import com.novaops.backend.auth.dto.RefreshTokenRequest;
import com.novaops.backend.auth.dto.RegisterRequest;
import com.novaops.backend.auth.dto.RoleResponse;
import com.novaops.backend.auth.dto.TenantInfoResponse;
import com.novaops.backend.auth.dto.UserProfileResponse;
import com.novaops.backend.auth.dto.UserListQuery;
import com.novaops.backend.auth.dto.UserListItemResponse;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.auth.mapper.AuthMapper;
import com.novaops.backend.auth.model.MenuRecord;
import com.novaops.backend.auth.model.InvitationRecord;
import com.novaops.backend.auth.model.RefreshTokenRecord;
import com.novaops.backend.auth.model.TenantRecord;
import com.novaops.backend.auth.model.UserRecord;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.security.JwtService;
import com.novaops.backend.common.util.IdGenerator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

  private final AuthMapper authMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final LoginFailureGuard loginFailureGuard;

  public AuthService(AuthMapper authMapper, PasswordEncoder passwordEncoder, JwtService jwtService, LoginFailureGuard loginFailureGuard) {
    this.authMapper = authMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.loginFailureGuard = loginFailureGuard;
  }

  @Transactional
  public LoginResponse login(LoginRequest request) {
    loginFailureGuard.assertNotLocked(request.getUsername());

    UserRecord user = authMapper.findUserByUsername(request.getUsername());

    String tenantId = StringUtils.hasText(request.getTenantId()) ? request.getTenantId() : "tenant-a";

    if (user == null) {
      loginFailureGuard.recordFailure(request.getUsername());
      throw new BusinessException(403, "账号或密码错误");
    }
    if (!Boolean.TRUE.equals(user.getEnabled())) {
      throw new BusinessException(403, "账号已被禁用");
    }
    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      loginFailureGuard.recordFailure(request.getUsername());
      throw new BusinessException(403, "账号或密码错误");
    }
    validateTenantAccess(user.getId(), tenantId);
    if (!user.getRoleId().equals(request.getRoleId())) {
      loginFailureGuard.recordFailure(request.getUsername());
      throw new BusinessException(403, "身份不匹配");
    }

    loginFailureGuard.reset(request.getUsername());
    return issueLoginSession(user, tenantId);
  }

  @Transactional
  public LoginResponse register(RegisterRequest request) {
    InvitationRecord invitation = authMapper.findInvitationByTokenHashForUpdate(SystemService.sha256(request.getInvitationToken()));
    if (invitation == null) {
      throw new BusinessException(400, "邀请无效");
    }
    if (invitation.getUsedAt() != null) {
      throw new BusinessException(400, "邀请已使用");
    }
    LocalDateTime now = LocalDateTime.now();
    if (invitation.getExpiresAt() == null || !invitation.getExpiresAt().isAfter(now)) {
      throw new BusinessException(400, "邀请已过期");
    }
    RoleResponse role = authMapper.findRoleById(invitation.getRoleId());
    if (role == null || (!"staff".equals(role.getCode()) && !"guest".equals(role.getCode()))) {
      throw new BusinessException(400, "邀请身份无效");
    }
    if (authMapper.findUserByUsername(request.getUsername()) != null) {
      throw new BusinessException(400, "用户名已存在");
    }

    UserRecord user = createUser(
        request.getUsername(), request.getPassword(), request.getDisplayName(), invitation.getTenantId(), role.getId());
    if (authMapper.consumeInvitation(invitation.getId(), now) != 1) {
      throw new BusinessException(400, "邀请已使用或已过期");
    }
    return issueLoginSession(user, invitation.getTenantId());
  }

  public AuthTokenResponse refresh(RefreshTokenRequest request) {
    RefreshTokenRecord record = authMapper.findRefreshToken(request.getRefreshToken());
    if (record == null || Boolean.TRUE.equals(record.getRevoked()) || record.getExpiresAt().isBefore(LocalDateTime.now())) {
      throw new BusinessException(401, "refresh token 已失效");
    }

    UserRecord user = authMapper.findUserById(record.getUserId());
    if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
      throw new BusinessException(401, "refresh token 已失效");
    }
    // 租户归属被移除后不能再凭旧 token 续期
    validateTenantAccess(user.getId(), record.getTenantId());

    // 轮换：旧的 refresh token 立即作废，签发新 token，降低泄露后的重放窗口
    authMapper.revokeRefreshToken(record.getToken());
    String rotatedRefreshToken = IdGenerator.randomId("rt");
    LocalDateTime expiresAt = LocalDateTime.now().plusDays(jwtService.getRefreshTokenExpireDays());
    authMapper.insertRefreshToken(rotatedRefreshToken, user.getId(), record.getTenantId(), expiresAt);
    return buildTokenResponse(user, record.getTenantId(), rotatedRefreshToken);
  }

  public UserProfileResponse me(CurrentSession session) {
    UserRecord user = authMapper.findUserById(session.getUserId());
    if (user == null) {
      throw new BusinessException(401, "token 无效");
    }

    UserProfileResponse response = new UserProfileResponse();
    response.setId(user.getId());
    response.setUsername(user.getUsername());
    response.setDisplayName(user.getDisplayName());
    response.setRoles(authMapper.listRolesByUserId(user.getId()));
    response.setPermissions(authMapper.listPermissions(user.getId(), session.getTenantId()));
    response.setTenantId(session.getTenantId());
    response.setTenants(authMapper.listTenantsByUserId(user.getId()).stream().map(this::toTenantInfo).toList());
    response.setPlatformAdmin(Boolean.TRUE.equals(user.getPlatformAdmin()));
    return response;
  }

  public MenuDataResponse menu(CurrentSession session) {
    UserRecord user = authMapper.findUserById(session.getUserId());
    if (user == null) {
      throw new BusinessException(401, "token 无效");
    }

    List<String> permissions = authMapper.listPermissions(user.getId(), session.getTenantId());
    List<String> roles = authMapper.listRolesByUserId(user.getId());
    String menuScope = roles.isEmpty() ? "guest" : resolveMenuScopeByRole(roles.get(0));
    List<MenuRecord> menuRecords = new ArrayList<>(authMapper.listMenusByScope(menuScope).stream()
        .filter(menu -> !StringUtils.hasText(menu.getPermissionCode()) || permissions.contains(menu.getPermissionCode()))
        .toList());
    if (Boolean.TRUE.equals(user.getPlatformAdmin())) {
      menuRecords.addAll(authMapper.listMenusByScope("platform"));
    }
    MenuDataResponse response = new MenuDataResponse();
    response.setPermissions(permissions);
    response.setMenus(buildMenuTree(menuRecords));
    return response;
  }

  public List<RoleResponse> listRoles() {
    List<RoleResponse> roles = authMapper.listRoles();
    roles.forEach(role -> role.setPermissions(authMapper.listPermissionsByRoleId(role.getId())));
    return roles;
  }

  public PageResult<UserListItemResponse> listUsers(CurrentSession session, UserListQuery query) {
    requireAdmin(session);
    int offset = (query.getPage() - 1) * query.getPageSize();
    // 只列出与当前管理员共享租户的用户，避免跨租户越权管理
    List<UserListItemResponse> list = authMapper.listUsers(session.getTenantId(), query.getKeyword(), query.getRoleId(), query.getEnabled(), offset, query.getPageSize());
    long total = authMapper.countUsers(session.getTenantId(), query.getKeyword(), query.getRoleId(), query.getEnabled());
    return new PageResult<>(list, query.getPage(), query.getPageSize(), total);
  }

  public void updateUserStatus(CurrentSession session, String userId, boolean enabled) {
    requireAdmin(session);
    if (session.getUserId().equals(userId) && !enabled) throw new BusinessException(400, "不能禁用当前登录账号");
    requireUser(userId);
    authMapper.updateUserStatus(userId, enabled);
    authMapper.revokeRefreshTokens(userId);
  }

  public void updateUserRole(CurrentSession session, String userId, String roleId) {
    requireAdmin(session);
    if (session.getUserId().equals(userId)) throw new BusinessException(400, "不能修改当前登录账号的身份");
    if (authMapper.findRoleById(roleId) == null) throw new BusinessException(400, "身份不存在");
    requireUser(userId);
    authMapper.updateUserRole(userId, roleId);
    authMapper.revokeRefreshTokens(userId);
  }

  public void resetPassword(CurrentSession session, String userId, String password) {
    requireAdmin(session);
    requireUser(userId);
    authMapper.updateUserPassword(userId, passwordEncoder.encode(password));
    authMapper.revokeRefreshTokens(userId);
  }

  public UserRecord requireEnabledUser(String userId) {
    UserRecord user = requireUser(userId);
    if (!Boolean.TRUE.equals(user.getEnabled())) throw new BusinessException(403, "账号已被禁用");
    return user;
  }

  private UserRecord requireUser(String userId) {
    UserRecord user = authMapper.findUserById(userId);
    if (user == null) throw new BusinessException(404, "用户不存在");
    return user;
  }

  private void requireAdmin(CurrentSession session) {
    UserRecord user = requireEnabledUser(session.getUserId());
    RoleResponse role = authMapper.findRoleById(user.getRoleId());
    if (role == null || !"admin".equals(role.getCode())) throw new BusinessException(403, "仅系统管理员可操作");
  }

  public void assertAdmin(CurrentSession session) {
    requireAdmin(session);
  }

  /** 校验当前会话在当前租户下是否持有权限码，供无法用注解表达的场景（如工单按动作细分） */
  public void requirePermission(CurrentSession session, String code) {
    if (!authMapper.listPermissions(session.getUserId(), session.getTenantId()).contains(code)) {
      throw new BusinessException(403, "无权限执行该操作");
    }
  }

  private LoginResponse issueLoginSession(UserRecord user, String tenantId) {
    String refreshToken = IdGenerator.randomId("rt");
    LocalDateTime expiresAt = LocalDateTime.now().plusDays(jwtService.getRefreshTokenExpireDays());
    authMapper.insertRefreshToken(refreshToken, user.getId(), tenantId, expiresAt);

    AuthTokenResponse tokenResponse = buildTokenResponse(user, tenantId, refreshToken);
    LoginResponse response = new LoginResponse();
    response.setAccessToken(tokenResponse.getAccessToken());
    response.setRefreshToken(tokenResponse.getRefreshToken());
    response.setExpiresIn(tokenResponse.getExpiresIn());
    response.setTenantId(tenantId);
    return response;
  }

  private AuthTokenResponse buildTokenResponse(UserRecord user, String tenantId, String refreshToken) {
    CurrentSession session = new CurrentSession(user.getId(), user.getUsername(), user.getDisplayName(), tenantId);
    AuthTokenResponse response = new AuthTokenResponse();
    response.setAccessToken(jwtService.createAccessToken(session));
    response.setRefreshToken(refreshToken);
    response.setExpiresIn(jwtService.getAccessTokenExpireSeconds());
    return response;
  }

  private void validateTenantAccess(String userId, String tenantId) {
    if (authMapper.countUserTenant(userId, tenantId) <= 0) {
      throw new BusinessException(403, "租户无权限访问");
    }
  }

  private UserRecord createUser(String username, String password, String displayName, String tenantId, String roleId) {
    UserRecord user = new UserRecord();
    user.setId(IdGenerator.randomId("usr"));
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setDisplayName(displayName);
    user.setRoleId(roleId);
    try {
      authMapper.insertUser(user);
    } catch (DuplicateKeyException exception) {
      throw new BusinessException(400, "用户名已存在");
    }
    authMapper.insertUserTenant(user.getId(), tenantId);
    return user;
  }

  private String resolveMenuScopeByRole(String roleCode) {
    if ("guest".equals(roleCode)) {
      return "guest";
    }
    if ("staff".equals(roleCode)) {
      return "staff";
    }
    return "full";
  }

  private TenantInfoResponse toTenantInfo(TenantRecord record) {
    TenantInfoResponse response = new TenantInfoResponse();
    response.setId(record.getId());
    response.setName(record.getName());
    return response;
  }

  private List<MenuItemResponse> buildMenuTree(List<MenuRecord> menuRecords) {
    Map<String, MenuItemResponse> menuMap = new LinkedHashMap<>();
    for (MenuRecord record : menuRecords) {
      MenuItemResponse response = new MenuItemResponse();
      response.setId(record.getId());
      response.setTitle(record.getTitle());
      response.setName(record.getName());
      response.setPath(record.getPath());
      response.setComponent(record.getComponent());
      response.setIcon(record.getIcon());
      response.setPermission(record.getPermissionCode());
      response.setKeepAlive(record.getKeepAlive());
      response.setChildren(new ArrayList<>());
      menuMap.put(record.getId(), response);
    }

    List<MenuItemResponse> roots = new ArrayList<>();
    for (MenuRecord record : menuRecords) {
      MenuItemResponse current = menuMap.get(record.getId());
      if (!StringUtils.hasText(record.getParentId())) {
        roots.add(current);
        continue;
      }
      MenuItemResponse parent = menuMap.get(record.getParentId());
      if (parent != null) {
        parent.getChildren().add(current);
      }
    }

    roots.forEach(this::pruneEmptyGroups);
    roots.removeIf(menu -> "RouteView".equals(menu.getComponent()) && menu.getChildren() == null);
    return roots;
  }

  private void pruneEmptyGroups(MenuItemResponse menu) {
    if (menu.getChildren() == null || menu.getChildren().isEmpty()) {
      menu.setChildren(null);
      return;
    }
    menu.getChildren().forEach(this::pruneEmptyGroups);
    menu.getChildren().removeIf(child -> "RouteView".equals(child.getComponent()) && child.getChildren() == null);
    if (menu.getChildren().isEmpty()) menu.setChildren(null);
  }
}
