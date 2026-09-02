package com.novaops.backend.agent.engine.model;

import java.util.Map;

/**
 * 引擎对外广播的事件（type: plan/step/audit/confirm_required/result/error）。
 * 任务层负责把事件写入会话队列（SSE）与审计持久化。
 */
public record EngineEvent(String type, int seq, Map<String, Object> payload) {

  public static EngineEvent of(String type, int seq, Map<String, ?> payload) {
    return new EngineEvent(type, seq, payload == null ? Map.of() : Map.copyOf(payload));
  }
}
