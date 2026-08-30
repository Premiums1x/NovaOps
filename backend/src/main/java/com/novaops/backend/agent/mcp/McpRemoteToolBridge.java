package com.novaops.backend.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.engine.AgentToolCategory;
import com.novaops.backend.agent.engine.ToolDescriptor;
import com.novaops.backend.agent.engine.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 远端 MCP 桥接：应用就绪后异步连接所有 enabled 的远端 MCP 服务器，
 * 把其只读工具桥接为本地 Agent 工具（名称 mcp_<server>_<tool>）。
 * 远端不可达只记告警，不影响内置工具与其余远端（fail-open 于发现、fail-closed 于调用）。
 */
@Component
public class McpRemoteToolBridge {

  private static final Logger log = LoggerFactory.getLogger(McpRemoteToolBridge.class);

  private final McpRemoteClient client;
  private final McpRemoteProperties properties;
  private final ToolRegistry toolRegistry;
  private final ObjectMapper objectMapper;

  public McpRemoteToolBridge(
      McpRemoteClient client, McpRemoteProperties properties, ToolRegistry toolRegistry,
      ObjectMapper objectMapper) {
    this.client = client;
    this.properties = properties;
    this.toolRegistry = toolRegistry;
    this.objectMapper = objectMapper;
  }

  @Async
  @EventListener(ApplicationReadyEvent.class)
  public void discoverAndRegister() {
    for (McpRemoteProperties.Server server : properties.getServers()) {
      if (!server.isEnabled() || server.getName() == null || server.getEndpoint() == null) {
        continue;
      }
      try {
        int registered = discoverAndRegister(server);
        log.info("MCP 远端工具桥接完成: server={}, tools={}", server.getName(), registered);
      } catch (Exception ex) {
        log.warn("MCP 远端不可达，跳过桥接: server={}, endpoint={}, 原因={}",
            server.getName(), server.getEndpoint(), ex.getMessage());
      }
    }
  }

  /** 供测试复用：返回成功注册的工具数。 */
  public int discoverAndRegister(McpRemoteProperties.Server server) {
    client.initialize(server.getEndpoint(), server.getToken(), server.getTimeoutSeconds());
    JsonNode tools = client.listTools(server.getEndpoint(), server.getToken(), server.getTimeoutSeconds());
    List<ToolDescriptor> registered = new ArrayList<>();
    if (tools.isArray()) {
      tools.forEach(tool -> {
        String remoteName = tool.path("name").asText(null);
        if (remoteName == null || remoteName.isBlank()) {
          return;
        }
        String mcpName = "mcp_" + server.getName() + "_" + remoteName;
        McpRemoteToolExecutor executor = new McpRemoteToolExecutor(
            McpServerConfig.of(server, client),
            remoteName,
            mcpName,
            "远端 MCP 工具（" + server.getName() + "）：" + tool.path("description").asText(""),
            objectMapper.convertValue(tool.path("inputSchema"), Map.class));
        ToolDescriptor descriptor = new ToolDescriptor(
            mcpName,
            mcpName,
            "远端 MCP 工具（" + server.getName() + "）：" + tool.path("description").asText(""),
            "",
            AgentToolCategory.READ,
            executor.inputSchema().build());
        if (toolRegistry.register(descriptor, executor)) {
          registered.add(descriptor);
        }
      });
    }
    return registered.size();
  }
}
