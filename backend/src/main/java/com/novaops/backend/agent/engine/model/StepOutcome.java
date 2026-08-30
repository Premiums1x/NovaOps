package com.novaops.backend.agent.engine.model;

/**
 * 一个步骤的执行结局（引擎按全局顺序编号）。observation 为截断后的 JSON 文本。
 */
public record StepOutcome(
    int seq,
    String tool,
    String title,
    String status,
    String observation,
    boolean write,
    boolean confirmed) {

  public static final String DONE = "DONE";
  public static final String FAILED = "FAILED";
  public static final String SKIPPED = "SKIPPED";
}
