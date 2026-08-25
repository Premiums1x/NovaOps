package com.novaops.backend.auth.service;

import com.novaops.backend.auth.dto.CreateInvitationRequest;
import com.novaops.backend.auth.dto.CreatedInvitationResponse;
import com.novaops.backend.auth.dto.CreateTenantRequest;
import com.novaops.backend.auth.dto.InvitationResponse;
import com.novaops.backend.auth.dto.TenantResponse;
import com.novaops.backend.auth.dto.RoleResponse;
import com.novaops.backend.auth.mapper.AuthMapper;
import com.novaops.backend.auth.model.InvitationRecord;
import com.novaops.backend.auth.model.TenantRecord;
import com.novaops.backend.auth.model.UserRecord;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.util.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemService {

  private static final String PERMISSION_TEMPLATE_TENANT_ID = "tenant-a";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final AuthMapper authMapper;
  private final Clock clock;

  public SystemService(AuthMapper authMapper, Clock clock) {
    this.authMapper = authMapper;
    this.clock = clock;
  }

  public List<TenantResponse> listTenants(CurrentSession session) {
    requirePlatformAdmin(session);
    return authMapper.listAllTenants().stream().map(this::toTenantResponse).toList();
  }

  @Transactional
  public TenantResponse createTenant(CurrentSession session, CreateTenantRequest request) {
    requirePlatformAdmin(session);
    if (authMapper.findTenantById(request.getCode()) != null) {
      throw new BusinessException(400, "租户代码已存在");
    }
    TenantRecord tenant = new TenantRecord();
    tenant.setId(request.getCode());
    tenant.setName(request.getName());
    try {
      authMapper.insertTenant(tenant);
    } catch (DuplicateKeyException exception) {
      throw new BusinessException(400, "租户代码已存在");
    }
    authMapper.cloneTenantRolePermissions(PERMISSION_TEMPLATE_TENANT_ID, tenant.getId());
    authMapper.insertUserTenant(session.getUserId(), tenant.getId());
    return toTenantResponse(tenant);
  }

  public List<InvitationResponse> listInvitations(CurrentSession session) {
    requirePlatformAdmin(session);
    return authMapper.listInvitations();
  }

  public CreatedInvitationResponse createInvitation(CurrentSession session, CreateInvitationRequest request) {
    requirePlatformAdmin(session);
    if (!"staff".equals(request.getRoleCode()) && !"guest".equals(request.getRoleCode())) {
      throw new BusinessException(400, "邀请身份只能是 staff 或 guest");
    }
    TenantRecord tenant = authMapper.findTenantById(request.getTenantId());
    if (tenant == null) {
      throw new BusinessException(400, "租户不存在");
    }
    RoleResponse role = authMapper.findRoleByCode(request.getRoleCode());
    if (role == null) {
      throw new BusinessException(400, "邀请身份不存在");
    }
    LocalDateTime now = LocalDateTime.now(clock);

    String rawToken = newRawToken();
    InvitationRecord invitation = new InvitationRecord();
    invitation.setId(IdGenerator.randomId("inv"));
    invitation.setTokenHash(sha256(rawToken));
    invitation.setTenantId(tenant.getId());
    invitation.setRoleId(role.getId());
    invitation.setCreatedBy(session.getUserId());
    invitation.setExpiresAt(now.plusDays(7));
    invitation.setCreatedAt(now);
    invitation.setUpdatedAt(now);
    authMapper.insertInvitation(invitation);

    CreatedInvitationResponse response = new CreatedInvitationResponse();
    response.setId(invitation.getId());
    response.setTenantId(invitation.getTenantId());
    response.setTenantName(tenant.getName());
    response.setRoleCode(role.getCode());
    response.setCreatedBy(invitation.getCreatedBy());
    response.setExpiresAt(invitation.getExpiresAt());
    response.setCreatedAt(invitation.getCreatedAt());
    response.setToken(rawToken);
    return response;
  }

  private void requirePlatformAdmin(CurrentSession session) {
    UserRecord user = authMapper.findUserById(session.getUserId());
    if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !Boolean.TRUE.equals(user.getPlatformAdmin())) {
      throw new BusinessException(403, "仅平台管理员可操作");
    }
    if (!"role-admin".equals(user.getRoleId())) {
      throw new BusinessException(403, "平台管理员必须绑定 role-admin 身份");
    }
  }

  private TenantResponse toTenantResponse(TenantRecord tenant) {
    TenantResponse response = new TenantResponse();
    response.setCode(tenant.getId());
    response.setName(tenant.getName());
    return response;
  }

  private String newRawToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
