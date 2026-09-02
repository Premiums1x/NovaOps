package com.novaops.backend.agent.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.engine.AgentTaskEngine;
import com.novaops.backend.agent.engine.ToolRegistry;
import com.novaops.backend.agent.engine.model.EngineEvent;
import com.novaops.backend.agent.engine.model.EngineListener;
import com.novaops.backend.agent.engine.model.EngineState;
import com.novaops.backend.agent.task.mapper.AgentAuditMapper;
import com.novaops.backend.agent.task.mapper.AgentTaskMapper;
import com.novaops.backend.auth.dto.MenuDataResponse;
import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
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
    when(authService.menu(any())).thenReturn(menu);
    when(toolRegistry.toolsFor(any())).thenReturn(List.of());
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
}
