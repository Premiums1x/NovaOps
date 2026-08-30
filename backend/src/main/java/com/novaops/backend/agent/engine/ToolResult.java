package com.novaops.backend.agent.engine;

/**
 * 工具执行结果。payload 会被引擎序列化为观察 JSON（统一截断）。
 */
public record ToolResult(Status status, Object payload, String message) {

  public enum Status { OK, EMPTY, FAILED, CONFIRM_REQUIRED }

  public static ToolResult ok(Object payload) {
    return new ToolResult(Status.OK, payload, null);
  }

  /** 执行成功但没有任何结果（引擎会据此触发有界重规划）。 */
  public static ToolResult empty(String message) {
    return new ToolResult(Status.EMPTY, null, message);
  }

  public static ToolResult failed(String message) {
    return new ToolResult(Status.FAILED, null, message);
  }

  /** WRITE 工具在 confirmed=false 时返回的效果预览，等待人工确认。 */
  public static ToolResult needsConfirmation(Object preview) {
    return new ToolResult(Status.CONFIRM_REQUIRED, preview, null);
  }
}
