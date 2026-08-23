package com.novaops.backend.auth.mapper;

import com.novaops.backend.auth.dto.RoleResponse;
import com.novaops.backend.auth.dto.UserListItemResponse;
import com.novaops.backend.auth.model.MenuRecord;
import com.novaops.backend.auth.model.RefreshTokenRecord;
import com.novaops.backend.auth.model.TenantRecord;
import com.novaops.backend.auth.model.UserRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AuthMapper {

  UserRecord findUserByUsername(@Param("username") String username);

  UserRecord findUserById(@Param("userId") String userId);

  List<String> listRolesByUserId(@Param("userId") String userId);

  List<String> listPermissions(@Param("userId") String userId, @Param("tenantId") String tenantId);

  List<TenantRecord> listTenantsByUserId(@Param("userId") String userId);

  TenantRecord findTenantById(@Param("tenantId") String tenantId);

  int countUserTenant(@Param("userId") String userId, @Param("tenantId") String tenantId);

  List<MenuRecord> listMenusByScope(@Param("menuScope") String menuScope);

  void insertRefreshToken(
      @Param("token") String token,
      @Param("userId") String userId,
      @Param("tenantId") String tenantId,
      @Param("expiresAt") LocalDateTime expiresAt
  );

  RefreshTokenRecord findRefreshToken(@Param("token") String token);

  // --- 注册 / 自动创建用户 ---

  void insertUser(UserRecord user);

  void insertUserTenant(@Param("userId") String userId, @Param("tenantId") String tenantId);

  List<RoleResponse> listRoles();

  RoleResponse findRoleById(@Param("roleId") String roleId);

  List<String> listPermissionsByRoleId(@Param("roleId") String roleId);
  List<UserListItemResponse> listUsers(@Param("keyword") String keyword, @Param("roleId") String roleId, @Param("enabled") Boolean enabled, @Param("offset") int offset, @Param("pageSize") int pageSize);
  long countUsers(@Param("keyword") String keyword, @Param("roleId") String roleId, @Param("enabled") Boolean enabled);
  void updateUserStatus(@Param("userId") String userId, @Param("enabled") Boolean enabled);
  void updateUserRole(@Param("userId") String userId, @Param("roleId") String roleId);
  void updateUserPassword(@Param("userId") String userId, @Param("passwordHash") String passwordHash);
  void revokeRefreshTokens(@Param("userId") String userId);
}
