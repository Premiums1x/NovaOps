package com.novaops.backend.agent.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.engine.ToolDescriptor;
import com.novaops.backend.agent.engine.model.EngineConfig;
import com.novaops.backend.agent.engine.model.EngineEvent;
import com.novaops.backend.agent.engine.model.EngineResult;
import com.novaops.backend.agent.engine.model.EngineState;
import com.novaops.backend.agent.engine.model.PlannedStep;
import com.novaops.backend.agent.engine.model.StepOutcome;
import com.novaops.backend.agent.engine.model.TaskPlan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentTaskEngineTest {

  // ---------- 假工具 ----------

  @AgentTool(name = "test.echo", title = "回显", description = "返回输入值")
  static class EchoTool implements AgentToolExecutor {
    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      return ToolResult.ok(Map.of("echo", String.valueOf(args.get("value"))));
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object().string("value", "要回显的值", true);
    }
  }

  @AgentTool(name = "test.empty", title = "总是空结果", description = "模拟空结果")
  static class EmptyTool implements AgentToolExecutor {
    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      return ToolResult.empty("没有匹配数据");
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object();
    }
  }

  @AgentTool(name = "test.huge", title = "超大结果", description = "返回超长内容")
  static class HugeTool implements AgentToolExecutor {
    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      return ToolResult.ok(Map.of("content", "x".repeat(5000)));
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object();
    }
  }

  @AgentTool(name = "test.guarded", title = "受权限保护", description = "需要 special:perm",
      permission = "special:perm")
  static class GuardedTool implements AgentToolExecutor {
    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      return ToolResult.ok("secret");
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object();
    }
  }

  @AgentTool(name = "test.write", title = "写操作", description = "两段式写操作",
      category = AgentToolCategory.WRITE)
  static class WriteTool implements AgentToolExecutor {
    boolean executedWithConfirm;

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      if (!confirmed) {
        return ToolResult.needsConfirmation(Map.of("preview", "将执行写操作"));
      }
      executedWithConfirm = true;
      return ToolResult.ok(Map.of("done", true));
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object();
    }
  }

  @AgentTool(name = "test.violating", title = "违约写工具", description = "未确认也返回成功",
      category = AgentToolCategory.WRITE)
  static class ViolatingTool implements AgentToolExecutor {
    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      return ToolResult.ok(Map.of("done", true));
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object();
    }
  }

  // ---------- 假模型网关 ----------

  static class ScriptedGateway implements TaskModelGateway {
    final Deque<TaskPlan> plans = new ArrayDeque<>();
    final Deque<TaskPlan> replans = new ArrayDeque<>();
    final Deque<String> summaries = new ArrayDeque<>();
    final List<List<ToolDescriptor>> planToolRequests =
        new ArrayList<>();
    final List<List<StepOutcome>> summarizeRequests = new ArrayList<>();
    int planCalls;
    int replanCalls;

    @Override
    public TaskPlan plan(String goal, List<ToolDescriptor> tools) {
      planCalls++;
      planToolRequests.add(tools);
      if (plans.isEmpty()) {
        throw new IllegalArgumentException("无脚本计划");
      }
      return plans.poll();
    }

    @Override
    public TaskPlan replan(String goal, List<ToolDescriptor> tools,
        List<StepOutcome> history) {
      replanCalls++;
      return replans.isEmpty() ? TaskPlan.of(List.of()) : replans.poll();
    }

    @Override
    public String summarize(String goal, List<StepOutcome> history) {
      summarizeRequests.add(history);
      return summaries.isEmpty() ? "默认总结" : summaries.poll();
    }
  }

  // ---------- 测试助手 ----------

  private static ToolRegistry registry() {
    return new ToolRegistry(List.of(
        new EchoTool(), new EmptyTool(), new HugeTool(), new GuardedTool(),
        new WriteTool(), new ViolatingTool()));
  }

  private static EngineState state(ToolRegistry registry, String goal, Set<String> permissions) {
    return new EngineState(
        "task-1", goal, new ToolContext("u-1", "tester", permissions), registry.toolsFor(permissions));
  }

  private static EngineConfig config(int maxSteps, int maxRevisions, int obsChars, int planRetries) {
    return new EngineConfig(maxSteps, maxRevisions, obsChars, 5, planRetries);
  }

  private static List<PlannedStep> steps(String... tools) {
    List<PlannedStep> result = new ArrayList<>();
    for (int i = 0; i < tools.length; i++) {
      result.add(new PlannedStep(i + 1, tools[i], Map.of("value", "v" + (i + 1)), "测试步骤"));
    }
    return result;
  }

  private static List<EngineEvent> run(AgentTaskEngine engine, EngineState state) {
    List<EngineEvent> events = new ArrayList<>();
    EngineResult result = engine.run(state, events::add);
    events.add(EngineEvent.of("phase", 0, Map.of("phase", result.phase().name())));
    return events;
  }

  private static EngineResult.Phase lastPhase(List<EngineEvent> events) {
    return EngineResult.Phase.valueOf(
        events.get(events.size() - 1).payload().get("phase").toString());
  }

  // ---------- 用例 ----------

  @Test
  void runsPlanStepsAndSummarizes() {
    ScriptedGateway gateway = new ScriptedGateway();
    gateway.plans.add(TaskPlan.of(steps("test.echo", "test.echo")));
    gateway.summaries.add("全部完成");
    ToolRegistry registry = registry();
    EngineState state = state(registry, "做两件事", Set.of());
    AgentTaskEngine engine = new AgentTaskEngine(
        registry, gateway, config(10, 2, 2000, 1), Runnable::run, new ObjectMapper());

    List<EngineEvent> events = run(engine, state);

    assertEquals(EngineResult.Phase.COMPLETED, lastPhase(events));
    assertEquals(2, state.outcomes().size());
    assertEquals(StepOutcome.DONE, state.outcomes().get(0).status());
    assertTrue(state.outcomes().get(0).observation().contains("v1"));
    assertEquals(1, gateway.summarizeRequests.size());
    assertEquals(2, gateway.summarizeRequests.get(0).size());
    assertEquals(List.of("plan", "step", "audit", "step", "audit", "result", "phase"),
        events.stream().map(EngineEvent::type).toList());
  }

  @Test
  void unavailableToolTriggersBoundedReplan() {
    ScriptedGateway gateway = new ScriptedGateway();
    gateway.plans.add(TaskPlan.of(steps("missing.tool", "test.echo")));
    gateway.replans.add(TaskPlan.of(steps("test.echo")));
    ToolRegistry registry = registry();
    EngineState state = state(registry, "用不存在的工具", Set.of());
    AgentTaskEngine engine = new AgentTaskEngine(
        registry, gateway, config(10, 2, 2000, 1), Runnable::run, new ObjectMapper());

    run(engine, state);

    assertEquals(1, gateway.replanCalls);
    assertEquals(StepOutcome.FAILED, state.outcomes().get(0).status());
    assertTrue(state.outcomes().get(0).observation().contains("工具不存在"));
    assertEquals(StepOutcome.DONE, state.outcomes().get(1).status());
  }

  @Test
  void permissionViolationsAreBlockedAndAudited() {
    ScriptedGateway gateway = new ScriptedGateway();
    gateway.plans.add(TaskPlan.of(steps("test.guarded")));
    gateway.replans.add(new TaskPlan(List.of(), true, "无法继续：缺少权限"));
    ToolRegistry registry = registry();
    EngineState state = state(registry, "越权尝试", Set.of());
    AgentTaskEngine engine = new AgentTaskEngine(
        registry, gateway, config(10, 2, 2000, 1), Runnable::run, new ObjectMapper());

    EngineResult result = engine.run(state, event -> { });

    assertEquals(EngineResult.Phase.FAILED, result.phase());
    assertTrue(state.outcomes().get(0).observation().contains("权限不足"));
    assertTrue(gateway.planToolRequests.get(0).stream()
        .noneMatch(descriptor -> "test.guarded".equals(descriptor.name())));
  }

  @Test
  void exceedingMaxStepsFailsClosedWithoutSummary() {
    ScriptedGateway gateway = new ScriptedGateway();
    gateway.plans.add(TaskPlan.of(steps("test.echo", "test.echo", "test.echo")));
    ToolRegistry registry = registry();
    EngineState state = state(registry, "步数超限", Set.of());
    AgentTaskEngine engine = new AgentTaskEngine(
        registry, gateway, config(2, 2, 2000, 1), Runnable::run, new ObjectMapper());

    EngineResult result = engine.run(state, event -> { });

    assertEquals(EngineResult.Phase.FAILED, result.phase());
    assertTrue(result.error().contains("最大步数上限"));
    assertEquals(2, state.outcomes().size());
    assertEquals(0, gateway.summarizeRequests.size());
  }

  @Test
  void planFailureRetriesThenFails() {
    ScriptedGateway gateway = new ScriptedGateway();
    ToolRegistry registry = registry();
    EngineState state = state(registry, "计划失败", Set.of());
    AgentTaskEngine engine = new AgentTaskEngine(
        registry, gateway, config(10, 2, 2000, 1), Runnable::run, new ObjectMapper());

    EngineResult result = engine.run(state, event -> { });

    assertEquals(EngineResult.Phase.FAILED, result.phase());
    assertEquals(2, gateway.planCalls);
  }

  @Test
  void emptyResultTriggersReplan() {
    ScriptedGateway gateway = new ScriptedGateway();
    gateway.plans.add(TaskPlan.of(steps("test.empty")));
    gateway.replans.add(TaskPlan.of(steps("test.echo")));
    ToolRegistry registry = registry();
    EngineState state = state(registry, "空结果", Set.of());
    AgentTaskEngine engine = new AgentTaskEngine(
        registry, gateway, config(10, 2, 2000, 1), Runnable::run, new ObjectMapper());

    run(engine, state);

    assertEquals(1, gateway.replanCalls);
    assertEquals(StepOutcome.DONE, state.outcomes().get(0).status());
    assertTrue(state.outcomes().get(0).observation().contains("没有匹配数据"));
  }

  @Test
  void observationIsTruncatedToConfiguredLimit() {
    ScriptedGateway gateway = new ScriptedGateway();
    gateway.plans.add(TaskPlan.of(steps("test.huge")));
    ToolRegistry registry = registry();
    EngineState state = state(registry, "超大观察", Set.of());
    AgentTaskEngine engine = new AgentTaskEngine(
        registry, gateway, config(10, 2, 200, 1), Runnable::run, new ObjectMapper());

    run(engine, state);

    String observation = state.outcomes().get(0).observation();
    assertEquals(200 + "…(已截断)".length(), observation.length());
  }

  @Test
  void writeToolPausesForConfirmationThenResumes() {
    ScriptedGateway gateway = new ScriptedGateway();
    gateway.plans.add(TaskPlan.of(steps("test.write", "test.echo")));
    gateway.summaries.add("写操作已完成");
    WriteTool writeTool = new WriteTool();
    ToolRegistry registry = new ToolRegistry(List.of(new EchoTool(), writeTool));
    EngineState state = state(registry, "写操作", Set.of());
    AgentTaskEngine engine = new AgentTaskEngine(
        registry, gateway, config(10, 2, 2000, 1), Runnable::run, new ObjectMapper());

    List<EngineEvent> firstRun = run(engine, state);
    assertEquals(EngineResult.Phase.AWAITING_CONFIRMATION, lastPhase(firstRun));
    assertNotNull(state.pendingConfirmationId());
    assertEquals(0, state.executedCount());
    EngineEvent confirmEvent = firstRun.stream()
        .filter(event -> "confirm_required".equals(event.type()))
        .findFirst().orElseThrow();
    assertTrue(confirmEvent.payload().get("preview").toString().contains("将执行写操作"));
    assertEquals("test.write", confirmEvent.payload().get("tool"));

    state.confirmApproved();
    List<EngineEvent> secondRun = run(engine, state);

    assertEquals(EngineResult.Phase.COMPLETED, lastPhase(secondRun));
    assertTrue(writeTool.executedWithConfirm);
    assertEquals(2, state.outcomes().size());
    assertTrue(state.outcomes().get(0).confirmed());
  }

  @Test
  void deniedWriteIsSkippedThenReplanned() {
    ScriptedGateway gateway = new ScriptedGateway();
    gateway.plans.add(TaskPlan.of(steps("test.write", "test.echo")));
    gateway.replans.add(TaskPlan.of(steps("test.echo")));
    ToolRegistry registry = new ToolRegistry(List.of(new EchoTool(), new WriteTool()));
    EngineState state = state(registry, "拒绝写操作", Set.of());
    AgentTaskEngine engine = new AgentTaskEngine(
        registry, gateway, config(10, 2, 2000, 1), Runnable::run, new ObjectMapper());

    List<EngineEvent> firstRun = run(engine, state);
    assertEquals(EngineResult.Phase.AWAITING_CONFIRMATION, lastPhase(firstRun));

    state.confirmDenied();
    List<EngineEvent> secondRun = run(engine, state);

    assertEquals(EngineResult.Phase.COMPLETED, lastPhase(secondRun));
    assertEquals(StepOutcome.SKIPPED, state.outcomes().get(0).status());
    assertEquals(StepOutcome.DONE, state.outcomes().get(1).status());
    assertEquals(1, gateway.replanCalls);
  }

  @Test
  void writeToolReturningSuccessWithoutConfirmationFailsClosed() {
    ScriptedGateway gateway = new ScriptedGateway();
    gateway.plans.add(TaskPlan.of(steps("test.violating")));
    ToolRegistry registry = registry();
    EngineState state = state(registry, "违约工具", Set.of());
    AgentTaskEngine engine = new AgentTaskEngine(
        registry, gateway, config(10, 2, 2000, 1), Runnable::run, new ObjectMapper());

    EngineResult result = engine.run(state, event -> { });

    assertEquals(EngineResult.Phase.FAILED, result.phase());
    assertTrue(result.error().contains("违反安全契约"));
  }

  @Test
  void confirmationIdHasFullUuidEntropy() {
    EngineState state = state(registry(), "确认令牌熵", Set.of());

    String id = state.mintConfirmationId();

    assertTrue(id.startsWith("confirm-"));
    assertEquals(32, id.substring("confirm-".length()).length());
  }

  @Test
  void stepEventsCarryPlanRevisionAndIncrementAfterReplan() {
    ScriptedGateway gateway = new ScriptedGateway();
    gateway.plans.add(TaskPlan.of(steps("missing.tool", "test.echo")));
    gateway.replans.add(TaskPlan.of(steps("test.echo")));
    ToolRegistry registry = registry();
    EngineState state = state(registry, "重规划版本", Set.of());
    AgentTaskEngine engine = new AgentTaskEngine(
        registry, gateway, config(10, 2, 2000, 1), Runnable::run, new ObjectMapper());

    List<EngineEvent> events = run(engine, state);

    List<Integer> revisions = events.stream()
        .filter(event -> "step".equals(event.type()))
        .map(event -> (Integer) event.payload().get("revision"))
        .toList();
    // 初始计划的步骤 revision=0，重规划后的新步骤 revision=1
    assertEquals(List.of(0, 1), revisions);
    assertEquals(1, gateway.replanCalls);
  }

  @Test
  void engineEventsCarryTimestamps() {
    ScriptedGateway gateway = new ScriptedGateway();
    gateway.plans.add(TaskPlan.of(steps("test.echo")));
    gateway.summaries.add("完成");
    ToolRegistry registry = registry();
    EngineState state = state(registry, "时间戳", Set.of());
    AgentTaskEngine engine = new AgentTaskEngine(
        registry, gateway, config(10, 2, 2000, 1), Runnable::run, new ObjectMapper());

    List<EngineEvent> events = run(engine, state);

    assertTrue(events.stream().allMatch(event -> event.at() > 0), "所有事件都应携带 at 时间戳");
  }

  @Test
  void toolPoolRejectionDegradesToStepFailureAndReplans() {
    ScriptedGateway gateway = new ScriptedGateway();
    gateway.plans.add(TaskPlan.of(steps("test.echo")));
    gateway.replans.add(TaskPlan.of(steps("test.echo")));
    gateway.summaries.add("降级后完成");
    ToolRegistry registry = registry();
    EngineState state = state(registry, "工具池拒绝", Set.of());
    //模拟工具池瞬时饱和：第一次提交被拒绝，之后恢复放行
    java.util.concurrent.atomic.AtomicInteger submissions = new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.Executor rejectingThenDelegatingRunner = command -> {
      if (submissions.getAndIncrement() == 0) {
        throw new java.util.concurrent.RejectedExecutionException("pool full");
      }
      command.run();
    };
    AgentTaskEngine engine = new AgentTaskEngine(
        registry, gateway, config(10, 2, 2000, 1), rejectingThenDelegatingRunner, new ObjectMapper());

    List<EngineEvent> events = run(engine, state);

    //池拒绝只影响该步（FAILED → 重规划），任务本身继续推进直至完成
    assertEquals(EngineResult.Phase.COMPLETED, lastPhase(events));
    assertEquals(1, gateway.replanCalls);
    assertEquals(StepOutcome.FAILED, state.outcomes().get(0).status());
    assertTrue(state.outcomes().get(0).observation().contains("队列已满"));
    assertEquals(StepOutcome.DONE, state.outcomes().get(1).status());
  }
}
