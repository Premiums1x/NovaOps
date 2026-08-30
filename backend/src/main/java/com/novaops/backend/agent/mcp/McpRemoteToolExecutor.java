package com.novaops.backend.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.novaops.backend.agent.engine.AgentToolExecutor;
import com.novaops.backend.agent.engine.ToolContext;
import com.novaops.backend.agent.engine.ToolResult;
import com.novaops.backend.agent.engine.ToolSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 远端 MCP 工具的本地执行器：把一次 execute 调用转成远端 tools/call。
 * 远端工具一律按 READ 类别接入（v1 不对远端开放写确认协议）。
 * 通过 {@link com.novaops.backend.agent.engine.ToolRegistry#register(
 * com.novaops.backend.agent.engine.ToolDescriptor, AgentToolExecutor)} 动态注册。
 */
public class McpRemoteToolExecutor implements AgentToolExecutor {

  private final ToolDescriptorHolder descriptor;
  private final String remoteToolName;
  private final McpServerConfig config;

  public McpRemoteToolExecutor(
      McpServerConfig config, String remoteToolName, String title,
      String description, Map<String, Object> inputSchema) {
    this.descriptor = new ToolDescriptorHolder(
        "mcp_" + sanitize(config.name()) + "_" + sanitize(remoteToolName),
        title,
        description,
        inputSchema);
    this.remoteToolName = remoteToolName;
    this.config = config;
  }

  private static String sanitize(String value) {
    return value.replaceAll("[^A-Za-z0-9]", "_").toLowerCase();
  }

  @Override
  public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
    try {
      JsonNode result = config.client().callTool(
          config.endpoint(), config.token(), config.timeoutSeconds(), remoteToolName, args);
      if (result.path("isError").asBoolean(false)) {
        return ToolResult.failed(textOf(result));
      }
      return ToolResult.ok(Map.of("text", textOf(result)));
    } catch (Exception ex) {
      return ToolResult.failed("远端工具调用失败：" + ex.getMessage());
    }
  }

  @Override
  public ToolSchema inputSchema() {
    return ToolSchema.fromMap(descriptor.inputSchema());
  }

  private static String textOf(JsonNode result) {
    List<String> texts = new ArrayList<>();
    result.path("content").forEach(node -> {
      if ("text".equals(node.path("type").asText())) {
        texts.add(node.path("text").asText());
      }
    });
    return String.join("\n", texts);
  }

  record ToolDescriptorHolder(String name, String title, String description, Map<String, Object> inputSchema) {
  }
}
