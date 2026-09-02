package com.novaops.backend.agent.task;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.engine.AgentTaskEngine;
import com.novaops.backend.agent.engine.AgentTool;
import com.novaops.backend.agent.engine.AgentToolCategory;
import com.novaops.backend.agent.engine.AgentToolExecutor;
import com.novaops.backend.agent.engine.TaskModelGateway;
import com.novaops.backend.agent.engine.ToolDescriptor;
import com.novaops.backend.agent.engine.ToolRegistry;
import com.novaops.backend.agent.engine.ToolContext;
import com.novaops.backend.agent.engine.ToolResult;
import com.novaops.backend.agent.engine.ToolSchema;
import com.novaops.backend.agent.engine.model.EngineConfig;
import com.novaops.backend.agent.engine.model.EngineEvent;
import com.novaops.backend.agent.engine.model.EngineListener;
import com.novaops.backend.agent.engine.model.EngineState;
import com.novaops.backend.agent.engine.model.PlannedStep;
import com.novaops.backend.agent.engine.model.StepOutcome;
import com.novaops.backend.agent.engine.model.TaskPlan;
import com.novaops.backend.agent.task.mapper.AgentAuditMapper;
import com.novaops.backend.agent.task.mapper.AgentTaskMapper;
import com.novaops.backend.agent.task.model.AgentAuditRecord;
import com.novaops.backend.agent.task.model.AgentTaskRecord;
import com.novaops.backend.agent.task.model.AgentTaskStepRecord;
import com.novaops.backend.auth.dto.MenuDataResponse;
import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 任务编排服务的关键守卫测试：
 * 引擎异常逃逸时任务必须落到 FAILED 并广播 error 事件；
 * 终态一旦写入不被后续事件/取消覆盖（SQL 幂等由 AgentTaskMapperTest 覆盖）。
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskServiceTest {

  @Mock
  private AgentTaskEngine engine;
  @Mock
  private ToolRegistry toolRegistry;
  @Mock
  private AuthService authService;
  @Mock
  private AgentTaskMapper taskMapper;
  @Mock
  private AgentAuditMapper auditMapper;

  private AgentTaskService service;
  private final CurrentSession session = new CurrentSession("user-1", "alice", "Alice");

  @BeforeEach
  void setUp() {
    //直接执行器：引擎在 create 调用线程内同步跑完，便于断言
    service = new AgentTaskService(
        engine, toolRegistry, authService, taskMapper, auditMapper,
        new ObjectMapper(), Runnable::run);
    MenuDataResponse menu = new MenuDataResponse();
    menu.setPermissions(List.of("agent:task"));
    lenient().when(authService.menu(any())).thenReturn(menu);
    lenient().when(toolRegistry.toolsFor(any())).thenReturn(List.of());
  }

  @Test
  void engineCrashMarksTerminalPersistsFailedAndBroadcastsError() {
    doAnswer(invocation -> {
      throw new RuntimeException("boom");
    }).when(engine).run(any(EngineState.class), any(EngineListener.class));

    Map<String, Object> created = service.create(session, "巡检核心交换机");
    String taskId = (String) created.get("taskId");

    verify(taskMapper).finishTask(taskId, "FAILED", null, "任务执行异常，请重新发起");
    AgentTaskSession agentSession = service.sessionIfPresent(taskId);
    assertTrue(agentSession.isTerminal());
    List<EngineEvent> history = agentSession.historySnapshot();
    assertEquals(1, history.size());
    assertEquals("error", history.get(0).type());
    assertEquals("任务执行异常，请重新发起", history.get(0).payload().get("message"));
  }

  @Test
  void cancelOnTerminalSessionDoesNotRewriteTerminal() {
    //引擎同步跑完并发 error 终态事件；随后到达的 cancel 不应再写 CANCELLED 覆盖 FAILED
    doAnswer(invocation -> {
      EngineListener listener = invocation.getArgument(1);
      listener.onEvent(EngineEvent.of("step", 1, Map.of("seq", 1, "tool", "ticket.search")));
      listener.onEvent(EngineEvent.of("error", 2, Map.of("message", "引擎失败")));
      return null;
    }).when(engine).run(any(EngineState.class), any(EngineListener.class));

    Map<String, Object> created = service.create(session, "巡检");
    String taskId = (String) created.get("taskId");

    service.cancel(session, taskId);

    verify(taskMapper, times(1)).finishTask(taskId, "FAILED", null, "引擎失败");
    verify(taskMapper, never()).finishTask(anyString(), eq("CANCELLED"), any(), any());
  }

  @Test
  void executorRejectionMarksTerminalAndFailsTask() {
    java.util.concurrent.Executor rejectingExecutor = command -> {
      throw new RejectedExecutionException("pool exhausted");
    };
    AgentTaskService rejectingService = new AgentTaskService(
        engine, toolRegistry, authService, taskMapper, auditMapper,
        new ObjectMapper(), rejectingExecutor);

    assertThrows(BusinessException.class, () -> rejectingService.create(session, "巡检"));

    verify(taskMapper).finishTask(anyString(), eq("FAILED"), isNull(), eq("服务繁忙，请稍后重试"));
  }

  // ---------- T4：挂起确认恢复 / TTL 清扫 / 会话驱逐 ----------

  @AgentTool(name = "test.write", title = "写操作", description = "两段式写操作",
      category = AgentToolCategory.WRITE)
  static class PausingWriteTool implements AgentToolExecutor {
    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      if (!confirmed) {
        return ToolResult.needsConfirmation(Map.of("ticketId", "A-1", "action", "assign"));
      }
      return ToolResult.ok(Map.of("done", true));
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object();
    }
  }

  /** 每次计划都包含一个未确认写步骤：引擎会停在 AWAITING_CONFIRMATION。 */
  static class PausePlanGateway implements TaskModelGateway {
    @Override
    public TaskPlan plan(String goal, List<ToolDescriptor> tools) {
      return TaskPlan.of(List.of(
          new PlannedStep(1, "test.write", Map.of("id", "A-1"), "需要确认的写操作")));
    }

    @Override
    public TaskPlan replan(String goal, List<ToolDescriptor> tools, List<StepOutcome> history) {
      return TaskPlan.of(List.of());
    }

    @Override
    public String summarize(String goal, List<StepOutcome> history) {
      return "完成";
    }
  }

  /** 真实引擎 + 挂起写工具的服务：任务创建后停在等待确认状态。 */
  private AgentTaskService pausedService() {
    ToolRegistry registry = new ToolRegistry(List.of(new PausingWriteTool()));
    AgentTaskEngine realEngine = new AgentTaskEngine(
        registry, new PausePlanGateway(), new EngineConfig(10, 2, 2000, 20, 1),
        Runnable::run, new ObjectMapper());
    return new AgentTaskService(
        realEngine, registry, authService, taskMapper, auditMapper,
        new ObjectMapper(), Runnable::run);
  }

  @Test
  void detailExposesPendingConfirmationForSuspendedTask() {
    AgentTaskService suspended = pausedService();
    String taskId = (String) suspended.create(session, "挂起任务").get("taskId");

    AgentTaskRecord record = new AgentTaskRecord();
    record.setId(taskId);
    record.setUserId("user-1");
    record.setGoal("挂起任务");
    record.setStatus("AWAITING_CONFIRM");
    when(taskMapper.findTask(taskId, "user-1")).thenReturn(record);
    AgentTaskStepRecord confirmStep = new AgentTaskStepRecord();
    confirmStep.setId("step-1");
    confirmStep.setTaskId(taskId);
    confirmStep.setSeq(1);
    confirmStep.setKind("confirm");
    confirmStep.setToolName("test.write");
    confirmStep.setObservationJson("{\"ticketId\":\"A-1\",\"action\":\"assign\"}");
    confirmStep.setStatus("AWAITING_CONFIRM");
    when(taskMapper.listSteps(taskId)).thenReturn(List.of(confirmStep));

    Map<String, Object> detail = suspended.detail(session, taskId);
    @SuppressWarnings("unchecked")
    Map<String, Object> pending = (Map<String, Object>) detail.get("pendingConfirmation");

    assertNotNull(pending);
    assertEquals("test.write", pending.get("tool"));
    assertEquals("写操作", pending.get("title"));
    assertEquals(suspended.sessionIfPresent(taskId).state().pendingConfirmationId(),
        pending.get("confirmationId"));
    @SuppressWarnings("unchecked")
    Map<String, Object> preview = (Map<String, Object>) pending.get("preview");
    assertEquals("A-1", preview.get("ticketId"));
  }

  @Test
  void sweepStaleTasksSkipsActiveSessionsAndFailsOrphans() {
    AgentTaskService suspended = pausedService();
    String activeId = (String) suspended.create(session, "活跃挂起任务").get("taskId");

    when(taskMapper.findStaleTaskIds(any())).thenReturn(List.of(activeId, "task_orphan"));

    int swept = suspended.sweepStaleTasks(java.time.Duration.ofMinutes(30));

    assertEquals(1, swept);
    verify(taskMapper).finishTask(
        eq("task_orphan"), eq("FAILED"), isNull(), eq("任务会话已失效，请重新发起"));
    verify(taskMapper, never()).finishTask(eq(activeId), any(), any(), any());
  }

  @Test
  void evictAllowsCapacityOverflowWithWarningThenSweepsTerminal() {
    AgentTaskService suspended = pausedService();
    for (int i = 0; i < 100; i++) {
      suspended.create(session, "批量任务" + i);
    }
    // 达到 100 上限后继续创建不抛错（告警放行）
    Map<String, Object> overflow = assertDoesNotThrow(() -> suspended.create(session, "超限任务"));
    String overflowId = (String) overflow.get("taskId");
    assertNotNull(overflowId);

    // 终态会话在下次 create 时被驱逐
    suspended.cancel(session, overflowId);
    assertDoesNotThrow(() -> suspended.create(session, "驱逐后任务"));
    assertNull(suspended.sessionIfPresent(overflowId));
  }

  // ---------- T7：审计明细接口与统计装配 ----------

  @Test
  void auditsRejectMissingOrForeignTask() {
    when(taskMapper.findTask("task-x", "user-1")).thenReturn(null);

    assertThrows(BusinessException.class, () -> service.audits(session, "task-x"));
    verify(auditMapper, never()).listAuditsByTask(anyString());
  }

  @Test
  void auditsReturnRecordsForOwnedTask() {
    AgentTaskRecord record = new AgentTaskRecord();
    record.setId("task-9");
    record.setUserId("user-1");
    when(taskMapper.findTask("task-9", "user-1")).thenReturn(record);
    List<AgentAuditRecord> audits = List.of(new AgentAuditRecord());
    when(auditMapper.listAuditsByTask("task-9")).thenReturn(audits);

    assertEquals(audits, service.audits(session, "task-9"));
  }

  @Test
  void statsAssemblesCountsFromMappers() {
    when(taskMapper.countTasksByStatus("user-1")).thenReturn(List.of(
        Map.of("status", "DONE", "cnt", 2L),
        Map.of("status", "FAILED", "cnt", 1L)));
    when(taskMapper.avgToolStepsPerTask("user-1")).thenReturn(1.5);
    when(auditMapper.writeAuditStats("user-1"))
        .thenReturn(Map.of("writeOperations", 3L, "confirmedOperations", 2L));

    Map<String, Object> stats = service.stats(session);

    assertEquals(3L, stats.get("total"));
    assertEquals(Map.of("DONE", 2L, "FAILED", 1L), stats.get("byStatus"));
    assertEquals(0.67, stats.get("successRate"));
    assertEquals(1.5, stats.get("avgSteps"));
    assertEquals(3L, stats.get("writeOperations"));
    assertEquals(2L, stats.get("confirmedOperations"));
  }

  @Test
  void statsHandlesEmptyHistory() {
    when(taskMapper.countTasksByStatus("user-1")).thenReturn(List.of());
    when(taskMapper.avgToolStepsPerTask("user-1")).thenReturn(0.0);
    when(auditMapper.writeAuditStats("user-1")).thenReturn(Map.of("writeOperations", 0L, "confirmedOperations", 0L));

    Map<String, Object> stats = service.stats(session);

    assertEquals(0L, stats.get("total"));
    assertEquals(0.0, stats.get("successRate"));
    assertEquals(0.0, stats.get("avgSteps"));
    assertEquals(0L, stats.get("writeOperations"));
  }
}
