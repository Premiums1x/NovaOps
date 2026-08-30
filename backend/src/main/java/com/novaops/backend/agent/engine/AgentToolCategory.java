package com.novaops.backend.agent.engine;

/**
 * 工具读写分级：READ 直接执行；WRITE 必须经过两段式人工确认。
 */
public enum AgentToolCategory {
  READ,
  WRITE
}
