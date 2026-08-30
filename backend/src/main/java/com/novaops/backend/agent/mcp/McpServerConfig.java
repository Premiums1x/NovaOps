package com.novaops.backend.agent.mcp;

/**
 * 一个已解析的远端 MCP 服务器配置（供执行器调用远端使用）。
 */
public record McpServerConfig(
    String name,
    String endpoint,
    String token,
    int timeoutSeconds,
    McpRemoteClient client) {

  public static McpServerConfig of(McpRemoteProperties.Server server, McpRemoteClient client) {
    return new McpServerConfig(
        server.getName(),
        server.getEndpoint(),
        server.getToken(),
        server.getTimeoutSeconds(),
        client);
  }
}
