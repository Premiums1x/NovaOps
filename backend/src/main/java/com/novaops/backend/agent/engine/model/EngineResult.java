package com.novaops.backend.agent.engine.model;

/**
 * 引擎一次推进的结局。phase=AWAITING_CONFIRMATION 时，确认详情在
 * {@link EngineState#pendingStep()} / pendingConfirmationId 中，等待人工落定后续跑。
 */
public record EngineResult(Phase phase, String summary, String error) {

  public enum Phase { COMPLETED, FAILED, AWAITING_CONFIRMATION }

  public static EngineResult completed(String summary) {
    return new EngineResult(Phase.COMPLETED, summary, null);
  }

  public static EngineResult failed(String error) {
    return new EngineResult(Phase.FAILED, null, error);
  }

  public static EngineResult awaitingConfirmation() {
    return new EngineResult(Phase.AWAITING_CONFIRMATION, null, null);
  }
}
