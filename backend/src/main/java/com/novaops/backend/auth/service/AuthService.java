package com.novaops.backend.auth.service;

import com.novaops.backend.auth.dto.AuthTokenResponse;
import com.novaops.backend.auth.dto.LoginRequest;
import com.novaops.backend.auth.dto.LoginResponse;
import com.novaops.backend.auth.dto.MenuDataResponse;
import com.novaops.backend.auth.dto.MenuItemResponse;
import com.novaops.backend.auth.dto.RefreshTokenRequest;
import com.novaops.backend.auth.dto.SwitchTenantRequest;
import com.novaops.backend.auth.dto.TenantInfoResponse;
import com.novaops.backend.auth.dto.UserProfileResponse;
import com.novaops.backend.auth.mapper.AuthMapper;
import com.novaops.backend.auth.model.MenuRecord;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

  private final AuthMapper authMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(AuthMapper authMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.authMapper = authMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  public LoginResponse login(LoginRequest request) {
    UserRecord user = authMapper.findUserByUsername(request.getUsername());
    if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new BusinessException(403, "用户名或密码错误");
    }

    String tenantId = StringUtils.hasText(request.getTenantId()) ? request.getTenantId() : "tenant-a";
    validateTenantAccess(user.getId(), tenantId);
    return issueLoginSession(user, tenantId);
  }

  public AuthTokenResponse refresh(RefreshTokenRequest request) {
    RefreshTokenRecord record = authMapper.findRefreshToken(request.getRefreshToken());
    if (record == null || Boolean.TRUE.equals(record.getRevoked()) || record.getExpiresAt().isBefore(LocalDateTime.now())) {
      throw new BusinessException(401, "refresh token 已失效");
    }

    UserRecord user = authMapper.findUserById(record.getUserId());
    if (user == null) {
      throw new BusinessException(401, "refresh token 已失效");
    }

    return buildTokenResponse(user, record.getTenantId(), record.getToken());
  }

  public LoginResponse switchTenant(CurrentSession session, SwitchTenantRequest request) {
    validateTenantAccess(session.getUserId(), request.getTenantId());
    UserRecord user = authMapper.findUserById(session.getUserId());
    if (user == null) {
      throw new BusinessException(401, "token 无效，无法切换租户");
    }
    return issueLoginSession(user, request.getTenantId());
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
    return response;
  }

  public MenuDataResponse menu(CurrentSession session) {
    UserRecord user = authMapper.findUserById(session.getUserId());
    if (user == null) {
      throw new BusinessException(401, "token 无效");
    }

    List<String> permissions = authMapper.listPermissions(user.getId(), session.getTenantId());
    List<MenuRecord> menuRecords = authMapper.listMenusByScope(resolveMenuScope(user.getUsername(), session.getTenantId()));
    MenuDataResponse response = new MenuDataResponse();
    response.setPermissions(permissions);
    response.setMenus(buildMenuTree(menuRecords));
    return response;
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

  private String resolveMenuScope(String username, String tenantId) {
    if ("guest".equals(username)) {
      return "guest";
    }
    if ("staff".equals(username) || "tenant-b".equals(tenantId)) {
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

    roots.forEach(this::normalizeChildren);
    return roots;
  }

  private void normalizeChildren(MenuItemResponse menu) {
    if (menu.getChildren() == null || menu.getChildren().isEmpty()) {
      menu.setChildren(null);
      return;
    }
    menu.getChildren().forEach(this::normalizeChildren);
  }
}
