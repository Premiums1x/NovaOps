package com.novaops.backend.auth.mapper;

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

  int countUserTenant(@Param("userId") String userId, @Param("tenantId") String tenantId);

  List<MenuRecord> listMenusByScope(@Param("menuScope") String menuScope);

  void insertRefreshToken(
      @Param("token") String token,
      @Param("userId") String userId,
      @Param("tenantId") String tenantId,
      @Param("expiresAt") LocalDateTime expiresAt
  );

  RefreshTokenRecord findRefreshToken(@Param("token") String token);
}
