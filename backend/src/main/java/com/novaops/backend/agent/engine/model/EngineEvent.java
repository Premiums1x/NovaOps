package com.novaops.backend.agent.engine.model;

import java.util.Map;

/**
 * 引擎对外广播的事件（type: plan/step/audit/confirm_required/result/error）。
 * at 为引擎侧产生时刻（毫秒 epoch），供 SSE 消费方计算每步耗时；
 * 任务层负责把事件写入会话队列（SSE）与审计持久化。
 */
public record EngineEvent(String type, int seq, long at, Map<String, Object> payload) {

  public static EngineEvent of(String type, int seq, Map<String, ?> payload) {
    return new EngineEvent(type, seq, System.currentTimeMillis(),
        payload == null ? Map.of() : Map.copyOf(payload));
  }
}
