package com.novaops.backend.agent.mcp;

import com.novaops.backend.agent.engine.AgentToolCategory;
import com.novaops.backend.agent.engine.ToolDescriptor;
import com.novaops.backend.agent.engine.ToolHandle;
import com.novaops.backend.agent.engine.ToolRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * MCP 工具目录：内部工具描述 ↔ MCP tools/list 载荷的映射层。
 * MCP 宿主普遍只接受 [A-Za-z0-9_-] 工具名，因此把内部的点分名映射为下划线名；
 * 该映射是 Server 侧唯一的名字事实来源（tools/list 与 tools/call 共用）。
 */
@Component
public class McpToolCatalog {

  private final ToolRegistry toolRegistry;

  public McpToolCatalog(ToolRegistry toolRegistry) {
    this.toolRegistry = toolRegistry;
  }

  /** 当前已暴露给 MCP 的只读工具描述（动态读取，远端桥接的新工具会随之出现）。 */
  public List<Map<String, Object>> listTools() {
    return toolRegistry.readableTools().stream()
        .map(McpToolCatalog::toMcpTool)
        .collect(Collectors.toList());
  }

  public ToolDescriptor findByMcpName(String mcpName) {
    ToolHandle handle = findHandleByMcpName(mcpName);
    return handle == null ? null : handle.descriptor();
  }

  /** 按 MCP 名找到工具句柄（描述 + 执行器）；不存在或非 READ 类返回 null。 */
  public ToolHandle findHandleByMcpName(String mcpName) {
    return toolRegistry.readableTools().stream()
        .filter(descriptor -> toMcpName(descriptor.name()).equals(mcpName))
        .findFirst()
        .flatMap(descriptor -> toolRegistry.find(descriptor.name()))
        .filter(handle -> handle.descriptor().category() == AgentToolCategory.READ)
        .orElse(null);
  }

  /** MCP 调用者的服务级权限：恰好等于所有被暴露只读工具的权限码集合。 */
  public Set<String> servicePermissions() {
    return toolRegistry.readableTools().stream()
        .map(ToolDescriptor::permission)
        .filter(permission -> permission != null && !permission.isBlank())
        .collect(Collectors.toSet());
  }

  public static String toMcpName(String internalName) {
    return internalName.replace('.', '_');
  }

  public static Map<String, Object> toMcpTool(ToolDescriptor descriptor) {
    Map<String, Object> tool = new LinkedHashMap<>();
    tool.put("name", toMcpName(descriptor.name()));
    tool.put("description", descriptor.title() + "：" + descriptor.description());
    tool.put("inputSchema", descriptor.inputSchema());
    return tool;
  }
}
