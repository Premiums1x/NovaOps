package com.novaops.backend.agent.engine.model;

import java.time.Duration;

/**
 * 引擎硬护栏配置。所有取值经钳制，防止配置出无限循环。
 */
public record EngineConfig(
    int maxSteps,
    int maxRevisions,
    int observationMaxChars,
    int stepTimeoutSeconds,
    int planRetries) {

  public EngineConfig {
    maxSteps = Math.max(1, maxSteps);
    maxRevisions = Math.max(0, maxRevisions);
    observationMaxChars = Math.max(200, observationMaxChars);
    stepTimeoutSeconds = Math.max(1, stepTimeoutSeconds);
    planRetries = Math.max(0, planRetries);
  }

  public static EngineConfig defaults() {
    return new EngineConfig(10, 2, 2000, 20, 1);
  }

  public Duration stepTimeout() {
    return Duration.ofSeconds(stepTimeoutSeconds);
  }
}
