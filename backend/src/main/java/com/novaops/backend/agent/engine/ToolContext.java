package com.novaops.backend.agent.engine;

import java.util.Set;

/**
 * 一次工具调用的上下文：调用者身份与其权限集合。
 * 工具拿到的权限集合已被注册表按任务用户解析过，工具不得自行扩大权限。
 */
public record ToolContext(String userId, String username, Set<String> permissions) {
  public boolean hasPermission(String code) {
    return code == null || code.isBlank() || permissions.contains(code);
  }
}
