package com.novaops.backend.agent.engine;

/**
 * 注册表内部句柄：描述 + 执行器。
 */
public record ToolHandle(ToolDescriptor descriptor, AgentToolExecutor executor) {
}
