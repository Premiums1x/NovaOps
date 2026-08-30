package com.novaops.backend.agent.engine;

import java.util.Map;

/**
 * 任务型 Agent 工具 SPI。实现类必须是 Spring Bean，由 {@link ToolRegistry} 收集。
 *
 * <p>实现约束：
 * <ul>
 *   <li>工具只能经由注入的业务 Service 操作数据，不得自行读写表；</li>
 *   <li>WRITE 类工具在 {@code confirmed=false} 时只做参数校验与效果预览，
 *       返回 {@link ToolResult#needsConfirmation}，不得产生副作用；在未确认状态下
 *       返回成功会被引擎按违反安全契约处理（fail-closed）；</li>
 *   <li>执行成功但没有任何结果时返回 {@link ToolResult#empty}，引擎会据此触发有界重规划。</li>
 * </ul>
 */
public interface AgentToolExecutor {

  ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed);

  ToolSchema inputSchema();
}
