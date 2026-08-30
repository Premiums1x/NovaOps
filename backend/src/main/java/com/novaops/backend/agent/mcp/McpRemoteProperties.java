package com.novaops.backend.agent.mcp;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 远端 MCP 服务器接入配置（app.agent.mcp.remote.servers[]）。
 * 默认 enabled=false：只有显式开启的远端才会在启动后被桥接为 Agent 工具。
 */
@ConfigurationProperties(prefix = "app.agent.mcp.remote")
public class McpRemoteProperties {

  private List<Server> servers = new ArrayList<>();

  public List<Server> getServers() {
    return servers;
  }

  public void setServers(List<Server> servers) {
    this.servers = servers;
  }

  public static class Server {
    private String name;
    private String endpoint;
    private String token;
    private boolean enabled = false;
    private int timeoutSeconds = 15;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }

    public String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }
  }
}
