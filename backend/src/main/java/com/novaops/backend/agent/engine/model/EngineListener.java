package com.novaops.backend.agent.engine.model;

/**
 * 引擎事件监听器：任务层实现它把事件送入会话队列（SSE）与审计持久化。
 * 引擎保证事件顺序与执行顺序一致。
 */
@FunctionalInterface
public interface EngineListener {
  void onEvent(EngineEvent event);
}
