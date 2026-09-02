package com.novaops.backend.agent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.engine.model.EngineConfig;
import com.novaops.backend.agent.engine.model.EngineEvent;
import com.novaops.backend.agent.engine.model.EngineListener;
import com.novaops.backend.agent.engine.model.EngineResult;
import com.novaops.backend.agent.engine.model.EngineState;
import com.novaops.backend.agent.engine.model.PlannedStep;
import com.novaops.backend.agent.engine.model.StepOutcome;
import com.novaops.backend.agent.engine.model.TaskPlan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * 受控 Plan-Act 引擎：模型负责"计划与总结"，代码负责"派发、校验、限额"。
 *
 * <p>核心规则（全部 fail-closed）：
 * <ul>
 *   <li>工具对模型不可见即不可调用：注册表按用户权限过滤提示词中的工具清单；</li>
 *   <li>步骤失败/空结果触发事件驱动的有界重规划（≤ maxRevisions），重规划耗尽则
 *       继续执行剩余计划并在总结中如实报告；</li>
 *   <li>WRITE 工具在未确认状态只允许返回效果预览；未确认却返回成功视为违反安全
 *       契约，任务立即失败；</li>
 *   <li>总步数 ≤ maxSteps，超限立即终止并给出机械的进度摘要（不经过模型）。</li>
 * </ul>
 *
 * <p>引擎是同步可推进的（tick 模型）：每次 run(state) 推进到 COMPLETED / FAILED /
 * AWAITING_CONFIRMATION。引擎不知道 SSE、数据库与具体业务。
 */
@Component
public class AgentTaskEngine {

  private static final String TRUNCATION_SUFFIX = "…(已截断)";

  private final ToolRegistry registry;
  private final TaskModelGateway gateway;
  private final EngineConfig config;
  private final Executor toolRunner;
  private final ObjectMapper objectMapper;

  public AgentTaskEngine(
      ToolRegistry registry,
      TaskModelGateway gateway,
      EngineConfig config,
      @org.springframework.beans.factory.annotation.Qualifier("agentToolExecutor")
      Executor toolRunner,
      ObjectMapper objectMapper) {
    this.registry = registry;
    this.gateway = gateway;
    this.config = config;
    this.toolRunner = toolRunner;
    this.objectMapper = objectMapper;
  }

  public EngineResult run(EngineState state, EngineListener listener) {
    try {
      if (state.isDenialPending()) {
        // 用户拒绝写操作：补记 SKIPPED 并触发重规划，让模型基于"被拒绝"调整后续动作
        // （必须先于"存在未落定确认"守卫：拒绝流程依赖保留的 pendingStep）
        PlannedStep denied = state.takeDeniedStep();
        if (denied == null) {
          return fail(state, listener, "拒绝状态异常：找不到被拒步骤");
        }
        StepOutcome outcome = new StepOutcome(
            state.nextGlobalSeq(), denied.tool(), toolTitle(denied.tool()),
            StepOutcome.SKIPPED, "用户拒绝执行该操作，未产生任何变更", true, false);
        state.addOutcome(outcome);
        emit(listener, "step", outcome.seq(), Map.of(
            "seq", outcome.seq(), "tool", outcome.tool(), "title", outcome.title(),
            "status", outcome.status(), "observation", outcome.observation(),
            "args", safeArgs(denied.args())));
        emit(listener, "audit", outcome.seq(),
            auditPayload(denied, outcome, false, false));
        ReplanOutcome replan = tryReplan(state, listener, "用户拒绝了一次写操作");
        if (replan == ReplanOutcome.ABORTED) {
          return fail(state, listener, "用户拒绝后无法继续：模型判断剩余目标无法达成");
        }
      }

      if (state.pendingStep() != null) {
        return fail(state, listener, "存在未落定的人工确认，不能直接续跑");
      }

      if (!state.isPlanMade()) {
        TaskPlan plan = planWithRetry(state, listener);
        state.markPlanMade();
        if (plan.abort()) {
          return fail(state, listener, plan.note() == null ? "模型判断目标无法达成" : plan.note());
        }
        state.replaceRemaining(plan.steps());
        emit(listener, "plan", 0, Map.of(
            "steps", plan.steps().stream()
                .map(step -> Map.of("seq", step.seq(), "tool", step.tool(),
                    "title", toolTitle(step.tool()), "why", step.why() == null ? "" : step.why()))
                .toList()));
      }

      while (state.hasRemainingSteps()) {
        if (state.executedCount() >= config.maxSteps()) {
          return fail(state, listener,
              "已达最大步数上限（" + config.maxSteps() + "），任务终止。" + progressDigest(state));
        }
        PlannedStep step = state.pollNextStep();

        var handle = registry.find(step.tool());
        if (handle.isEmpty()) {
          if (!handleFailure(state, listener, step,
              "工具不存在：" + step.tool() + "，已拦截", false)) {
            return failedDigest(state, listener);
          }
          continue;
        }
        var descriptor = handle.get().descriptor();
        if (!ToolRegistry.permitted(descriptor, state.ctx().permissions())) {
          if (!handleFailure(state, listener, step,
              "权限不足，调用已拦截：" + step.tool(), false)) {
            return failedDigest(state, listener);
          }
          continue;
        }

        boolean confirmed = state.isConfirmed(step);
        ToolResult result = invoke(handle.get(), state.ctx(), step.args(), confirmed);

        if (descriptor.category() == AgentToolCategory.WRITE && !confirmed
            && result.status() == ToolResult.Status.OK) {
          return fail(state, listener,
              "写操作工具在未确认状态下返回成功，违反安全契约，任务终止：" + step.tool());
        }
        if (result.status() == ToolResult.Status.CONFIRM_REQUIRED) {
          if (confirmed) {
            return fail(state, listener, "已确认的工具再次要求确认，违反安全契约：" + step.tool());
          }
          String confirmationId = state.mintConfirmationId();
          state.beginConfirmation(step, confirmationId);
          int displaySeq = state.executedCount() + 1;
          emit(listener, "confirm_required", displaySeq, Map.of(
              "seq", displaySeq,
              "tool", step.tool(),
              "title", descriptor.title(),
              "why", step.why() == null ? "" : step.why(),
              "args", safeArgs(step.args()),
              "preview", result.payload() == null ? Map.of() : result.payload(),
              "confirmationId", confirmationId));
          return EngineResult.awaitingConfirmation();
        }

        StepOutcome outcome = toOutcome(state, descriptor, step, result, confirmed);
        state.addOutcome(outcome);
        emit(listener, "step", outcome.seq(), Map.of(
            "seq", outcome.seq(), "tool", outcome.tool(), "title", outcome.title(),
            "status", outcome.status(), "observation", outcome.observation(),
            "args", safeArgs(step.args())));
        emit(listener, "audit", outcome.seq(),
            auditPayload(step, outcome, true, confirmed));

        if (result.status() == ToolResult.Status.FAILED
            || result.status() == ToolResult.Status.EMPTY) {
          ReplanOutcome replan = tryReplan(state, listener,
              result.message() == null ? "步骤未产生结果" : result.message());
          if (replan == ReplanOutcome.ABORTED) {
            return failedDigest(state, listener);
          }
          // EXHAUSTED（重规划额度用尽）则继续执行剩余计划，总结时如实报告
        }
      }

      String summary = gateway.summarize(state.goal(), state.outcomes());
      emit(listener, "result", 0, Map.of("summary", summary == null ? "" : summary));
      return EngineResult.completed(summary);
    } catch (Exception ex) {
      return fail(state, listener, "任务执行异常：" + rootMessage(ex));
    }
  }

  private TaskPlan planWithRetry(EngineState state, EngineListener listener) throws Exception {
    Exception last = null;
    for (int attempt = 0; attempt <= config.planRetries(); attempt++) {
      try {
        return gateway.plan(state.goal(), state.tools());
      } catch (Exception ex) {
        last = ex;
      }
    }
    throw last;
  }

  private ReplanOutcome tryReplan(EngineState state, EngineListener listener, String reason)
      throws Exception {
    if (state.revisions() >= config.maxRevisions()) {
      return ReplanOutcome.EXHAUSTED;
    }
    TaskPlan newPlan = gateway.replan(state.goal(), state.tools(), state.outcomes());
    state.incrementRevisions();
    if (newPlan.abort()) {
      emit(listener, "error", 0, Map.of("message",
          newPlan.note() == null ? "模型判断目标无法达成" : newPlan.note()));
      return ReplanOutcome.ABORTED;
    }
    state.replaceRemaining(newPlan.steps());
    emit(listener, "plan", 0, Map.of(
        "revision", state.revisions(),
        "reason", reason == null ? "" : reason,
        "steps", newPlan.steps().stream()
            .map(step -> Map.of("seq", step.seq(), "tool", step.tool(),
                "title", toolTitle(step.tool()), "why", step.why() == null ? "" : step.why()))
            .toList()));
    return ReplanOutcome.REPLANNED;
  }

  /** 步骤失败/工具不可用/权限不足的统一处理：记录结局并尝试重规划。返回 false 表示任务应终止。 */
  private boolean handleFailure(
      EngineState state, EngineListener listener, PlannedStep step,
      String observation, boolean executed) throws Exception {
    StepOutcome outcome = new StepOutcome(
        state.nextGlobalSeq(), step.tool(), toolTitle(step.tool()),
        StepOutcome.FAILED, observation, false, false);
        state.addOutcome(outcome);
        emit(listener, "step", outcome.seq(), Map.of(
            "seq", outcome.seq(), "tool", outcome.tool(), "title", outcome.title(),
            "status", outcome.status(), "observation", observation,
            "args", safeArgs(step.args())));
        emit(listener, "audit", outcome.seq(),
            auditPayload(step, outcome, executed, false));
    ReplanOutcome replan = tryReplan(state, listener, observation);
    return replan != ReplanOutcome.ABORTED;
  }

  private StepOutcome toOutcome(
      EngineState state, ToolDescriptor descriptor, PlannedStep step,
      ToolResult result, boolean confirmed) {
    String observation = switch (result.status()) {
      case OK -> truncate(toJson(result.payload()));
      case EMPTY -> "执行成功但没有结果：" + (result.message() == null ? "无" : result.message());
      case FAILED -> "执行失败：" + (result.message() == null ? "无" : result.message());
      default -> "";
    };
    String status = result.status() == ToolResult.Status.FAILED
        ? StepOutcome.FAILED
        : StepOutcome.DONE;
    return new StepOutcome(
        state.nextGlobalSeq(), step.tool(), descriptor.title(), status,
        observation, descriptor.category() == AgentToolCategory.WRITE, confirmed);
  }

  private ToolResult invoke(ToolHandle handle, ToolContext ctx, Map<String, Object> args, boolean confirmed)
      throws Exception {
    Callable<ToolResult> call = () -> handle.executor().execute(ctx, args, confirmed);
    if (toolRunner == null) {
      return call.call();
    }
    FutureTask<ToolResult> future = new FutureTask<>(call);
    try {
      toolRunner.execute(future);
    } catch (RejectedExecutionException ex) {
      // 工具池拒绝：降级为该步失败并走重规划，不放大成整个任务失败
      return ToolResult.failed("工具执行队列已满，请稍后重试");
    }
    try {
      return future.get(config.stepTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      future.cancel(true);
      return ToolResult.failed("工具执行超时（>" + config.stepTimeoutSeconds() + "s）");
    } catch (ExecutionException ex) {
      return ToolResult.failed("工具执行异常：" + rootMessage(ex.getCause() == null ? ex : ex.getCause()));
    }
  }

  private EngineResult failedDigest(EngineState state, EngineListener listener) {
    return fail(state, listener, "模型判断剩余目标无法达成。" + progressDigest(state));
  }

  private EngineResult fail(EngineState state, EngineListener listener, String message) {
    emit(listener, "error", 0, Map.of(
        "message", message,
        "progress", progressDigest(state)));
    return EngineResult.failed(message);
  }

  private String progressDigest(EngineState state) {
    long done = state.outcomes().stream().filter(o -> StepOutcome.DONE.equals(o.status())).count();
    long failed = state.outcomes().stream().filter(o -> StepOutcome.FAILED.equals(o.status())).count();
    long skipped = state.outcomes().stream().filter(o -> StepOutcome.SKIPPED.equals(o.status())).count();
    return "进度：已执行 " + state.executedCount() + " 步（成功 " + done
        + " / 失败 " + failed + " / 跳过 " + skipped + "）。";
  }

  private Map<String, Object> auditPayload(
      PlannedStep step, StepOutcome outcome, boolean allowed, boolean confirmed) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tool", outcome.tool());
    payload.put("args", safeArgs(step.args()));
    payload.put("status", outcome.status());
    payload.put("observation", outcome.observation());
    payload.put("write", outcome.write());
    payload.put("confirmed", outcome.confirmed());
    payload.put("allowed", allowed);
    return payload;
  }

  private String toolTitle(String toolName) {
    return registry.find(toolName)
        .map(handle -> handle.descriptor().title())
        .orElse(toolName);
  }

  private Map<String, Object> safeArgs(Map<String, Object> args) {
    return args == null ? Map.of() : Map.copyOf(args);
  }

  private String toJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
    } catch (Exception ex) {
      return String.valueOf(payload);
    }
  }

  private String truncate(String text) {
    if (text == null || text.length() <= config.observationMaxChars()) {
      return text == null ? "" : text;
    }
    return text.substring(0, config.observationMaxChars()) + TRUNCATION_SUFFIX;
  }

  private void emit(EngineListener listener, String type, int seq, Map<String, Object> payload) {
    listener.onEvent(EngineEvent.of(type, seq, payload));
  }

  private String rootMessage(Throwable ex) {
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
  }

  private enum ReplanOutcome { REPLANNED, EXHAUSTED, ABORTED }
}
