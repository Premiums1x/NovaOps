package com.novaops.backend.agent.engine.model;

import com.novaops.backend.agent.engine.ToolContext;
import com.novaops.backend.agent.engine.ToolDescriptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 引擎的可推进运行状态（tick 模型）：任务层持有它，run(state) 每次推进到
 * COMPLETED / FAILED / AWAITING_CONFIRMATION 为止；确认落定后续跑即可。
 * 引擎是唯一的业务写入口，保证循环护栏（步数/重规划上限）不被外层破坏。
 */
public class EngineState {

  private final String taskId;
  private final String goal;
  private final ToolContext ctx;
  private final List<ToolDescriptor> tools;
  private final List<StepOutcome> outcomes = new ArrayList<>();
  private final Set<Integer> confirmedSeqs = new HashSet<>();
  private final Deque<PlannedStep> remaining = new ArrayDeque<>();

  private int revisions;
  private int globalSeq;
  private PlannedStep pendingStep;
  private String pendingConfirmationId;
  private boolean planMade;
  private boolean denialPending;

  public EngineState(String taskId, String goal, ToolContext ctx, List<ToolDescriptor> tools) {
    this.taskId = taskId;
    this.goal = goal;
    this.ctx = ctx;
    this.tools = List.copyOf(tools);
  }

  public String taskId() {
    return taskId;
  }

  public String goal() {
    return goal;
  }

  public ToolContext ctx() {
    return ctx;
  }

  public List<ToolDescriptor> tools() {
    return tools;
  }

  public List<StepOutcome> outcomes() {
    return List.copyOf(outcomes);
  }

  public int revisions() {
    return revisions;
  }

  public int executedCount() {
    return outcomes.size();
  }

  public PlannedStep pendingStep() {
    return pendingStep;
  }

  public String pendingConfirmationId() {
    return pendingConfirmationId;
  }

  public boolean isPlanMade() {
    return planMade;
  }

  public void markPlanMade() {
    planMade = true;
  }

  public boolean hasRemainingSteps() {
    return !remaining.isEmpty();
  }

  public PlannedStep pollNextStep() {
    return remaining.poll();
  }

  public void replaceRemaining(List<PlannedStep> steps) {
    remaining.clear();
    remaining.addAll(steps);
  }

  public void incrementRevisions() {
    revisions++;
  }

  public int nextGlobalSeq() {
    return ++globalSeq;
  }

  public void addOutcome(StepOutcome outcome) {
    outcomes.add(outcome);
  }

  public void beginConfirmation(PlannedStep step, String confirmationId) {
    this.pendingStep = step;
    this.pendingConfirmationId = confirmationId;
  }

  /** 批准确认：该步获得确认标记并回到队列队首，续跑时以 confirmed=true 执行。 */
  public void confirmApproved() {
    if (pendingStep != null) {
      confirmedSeqs.add(pendingStep.seq());
      remaining.addFirst(pendingStep);
    }
    pendingStep = null;
    pendingConfirmationId = null;
  }

  /** 拒绝确认：保留被拒步骤供引擎补记 SKIPPED 结局并触发有界重规划。 */
  public void confirmDenied() {
    denialPending = true;
  }

  public boolean isDenialPending() {
    return denialPending;
  }

  /** 引擎读取被拒步骤并结束确认挂起。 */
  public PlannedStep takeDeniedStep() {
    PlannedStep denied = pendingStep;
    pendingStep = null;
    pendingConfirmationId = null;
    denialPending = false;
    return denied;
  }

  public boolean isConfirmed(PlannedStep step) {
    return confirmedSeqs.contains(step.seq());
  }

  public String mintConfirmationId() {
    return "confirm-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }
}
