package com.novaops.backend.agent.engine.model;

import java.util.List;

/**
 * 模型产出的计划。abort=true 表示模型判断目标无法达成（如缺少必要工具），
 * note 用于向用户解释。
 */
public record TaskPlan(List<PlannedStep> steps, boolean abort, String note) {

  public static TaskPlan of(List<PlannedStep> steps) {
    return new TaskPlan(List.copyOf(steps), false, null);
  }
}
