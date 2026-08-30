package com.novaops.backend.agent.engine;

import java.util.Map;

/**
 * 工具的静态描述（不含执行器引用），用于：模型计划提示词、MCP tools/list、前端展示。
 */
public record ToolDescriptor(
    String name,
    String title,
    String description,
    String permission,
    AgentToolCategory category,
    Map<String, Object> inputSchema) {
}
