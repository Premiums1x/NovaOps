package com.novaops.backend.agent.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.engine.AgentTaskEngine;
import com.novaops.backend.agent.engine.ToolContext;
import com.novaops.backend.agent.engine.ToolRegistry;
import com.novaops.backend.agent.engine.model.EngineEvent;
import com.novaops.backend.agent.engine.model.EngineState;
import com.novaops.backend.agent.task.mapper.AgentAuditMapper;
import com.novaops.backend.agent.task.mapper.AgentTaskMapper;
import com.novaops.backend.agent.task.model.AgentAuditRecord;
import com.novaops.backend.agent.task.model.AgentTaskRecord;
import com.novaops.backend.agent.task.model.AgentTaskStepRecord;
import com.novaops.backend.auth.dto.MenuDataResponse;
import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.util.IdGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.stereotype.Service;

/**
 * 任务型 Agent 的编排服务：任务生命周期（创建/流式/确认/取消）、
 * 事件广播、步骤与审计持久化。任务会话保存在内存（不跨重启）。
 */
@Service
public class AgentTaskService {

  private static final Logger log = LoggerFactory.getLogger(AgentTaskService.class);
  private static final int MAX_SESSIONS = 100;

  private final AgentTaskEngine engine;
  private final ToolRegistry toolRegistry;
  private final AuthService authService;
  private final AgentTaskMapper taskMapper;
  private final AgentAuditMapper auditMapper;
  private final ObjectMapper objectMapper;
  private final Executor taskExecutor;
  private final Map<String, AgentTaskSession> sessions = new ConcurrentHashMap<>();

  public AgentTaskService(
      AgentTaskEngine engine,
      ToolRegistry toolRegistry,
      AuthService authService,
      AgentTaskMapper taskMapper,
      AgentAuditMapper auditMapper,
      ObjectMapper objectMapper,
      @Qualifier("agentTaskExecutor") Executor taskExecutor) {
    this.engine = engine;
    this.toolRegistry = toolRegistry;
    this.authService = authService;
    this.taskMapper = taskMapper;
    this.auditMapper = auditMapper;
    this.objectMapper = objectMapper;
    this.taskExecutor = taskExecutor;
  }

  public Map<String, Object> create(CurrentSession session, String goal) {
    Set<String> permissions = Set.copyOf(authService.menu(session).getPermissions());
    EngineState state = new EngineState(
        IdGenerator.randomId("task"),
        goal,
        new ToolContext(session.getUserId(), session.getUsername(), permissions),
        toolRegistry.toolsFor(permissions));
    AgentTaskSession agentSession = new AgentTaskSession(state.taskId(), state);

    evictOldSessions();
    sessions.put(state.taskId(), agentSession);

    AgentTaskRecord record = new AgentTaskRecord();
    record.setId(state.taskId());
    record.setUserId(session.getUserId());
    record.setGoal(goal);
    record.setStatus("RUNNING");
    taskMapper.insertTask(record);
    submit(session, state, agentSession);
    return Map.of("taskId", state.taskId());
  }

  /** 启动/续跑引擎：终态事件先标记会话（让广播正确关闭流），再落库、再广播。 */
  private void submit(CurrentSession owner, EngineState state, AgentTaskSession agentSession) {
    try {
      agentSession.future(java.util.concurrent.CompletableFuture.runAsync(() -> {
        engine.run(state, event -> {
          if ("result".equals(event.type()) || "error".equals(event.type())) {
            agentSession.markTerminal();
          }
          persist(owner, state.taskId(), event);
          agentSession.record(event);
        });
      }, taskExecutor));
    } catch (RejectedExecutionException ex) {
      agentSession.markTerminal();
      taskMapper.finishTask(state.taskId(), "FAILED", null, "服务繁忙，请稍后重试");
      throw new BusinessException(429, "智能体服务繁忙，请稍后重试");
    }
  }

  public SseEmitter openStream(CurrentSession session, String taskId) {
    AgentTaskSession agentSession = requireSession(session, taskId);
    return agentSession.attach();
  }

  public Map<String, Object> confirm(
      CurrentSession session, String taskId, String confirmationId, boolean approved) {
    AgentTaskSession agentSession = requireSession(session, taskId);
    EngineState state = agentSession.state();
    if (agentSession.isTerminal()
        || state.pendingConfirmationId() == null
        || !state.pendingConfirmationId().equals(confirmationId)) {
      throw new BusinessException(400, "确认请求无效或已过期");
    }
    if (approved) {
      state.confirmApproved();
      taskMapper.updateTaskStatus(taskId, "RUNNING");
    } else {
      state.confirmDenied();
      taskMapper.updateTaskStatus(taskId, "RUNNING");
    }
    submit(session, state, agentSession);
    return Map.of("taskId", taskId, "approved", approved);
  }

  public void cancel(CurrentSession session, String taskId) {
    AgentTaskSession agentSession = requireSession(session, taskId);
    if (!agentSession.isTerminal()) {
      agentSession.markTerminal();
      Future<?> future = agentSession.future();
      if (future != null) {
        future.cancel(true);
      }
      taskMapper.finishTask(taskId, "CANCELLED", null, "用户取消任务");
    }
  }

  public Map<String, Object> detail(CurrentSession session, String taskId) {
    AgentTaskRecord task = taskMapper.findTask(taskId, session.getUserId());
    if (task == null) {
      throw new BusinessException(404, "任务不存在");
    }
    List<Map<String, Object>> steps = taskMapper.listSteps(taskId).stream()
        .map(this::toStepView)
        .toList();
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("task", task);
    view.put("steps", steps);
    return view;
  }

  public List<AgentTaskRecord> list(CurrentSession session) {
    return taskMapper.listTasks(session.getUserId(), 50);
  }

  private AgentTaskSession requireSession(CurrentSession session, String taskId) {
    AgentTaskSession agentSession = sessions.get(taskId);
    if (agentSession == null) {
      throw new BusinessException(404, "任务会话不存在或已失效");
    }
    if (!agentSession.state().ctx().userId().equals(session.getUserId())) {
      throw new BusinessException(403, "无权访问该任务");
    }
    return agentSession;
  }

  /** 事件持久化：步骤/审计落库，终态回写任务表（广播由会话处理）。 */
  private void persist(CurrentSession owner, String taskId, EngineEvent event) {
    try {
      switch (event.type()) {
        case "plan" -> taskMapper.updatePlanJson(taskId, toJson(event.payload().get("steps")));
        case "step" -> taskMapper.insertStep(stepRecord(taskId, event, "tool", "DONE"));
        case "confirm_required" -> {
          taskMapper.insertStep(stepRecord(taskId, event, "confirm", "AWAITING_CONFIRM"));
          taskMapper.updateTaskStatus(taskId, "AWAITING_CONFIRM");
        }
        case "result" -> {
          taskMapper.insertStep(stepRecord(taskId, event, "summary", "DONE"));
          taskMapper.finishTask(taskId, "DONE",
              String.valueOf(event.payload().get("summary")), null);
        }
        case "error" -> taskMapper.finishTask(
            taskId, "FAILED", null, String.valueOf(event.payload().get("message")));
        case "audit" -> auditMapper.insertAudit(auditRecord(owner, taskId, event));
        default -> {
          // 引擎后续若新增事件类型，未落库也不影响流式
        }
      }
    } catch (Exception ex) {
      log.warn("agent task event persistence failed: taskId={}, type={}", taskId, event.type(), ex);
    }
  }

  private AgentTaskStepRecord stepRecord(String taskId, EngineEvent event, String kind, String status) {
    AgentTaskStepRecord record = new AgentTaskStepRecord();
    record.setId(IdGenerator.randomId("tstep"));
    record.setTaskId(taskId);
    record.setSeq((Integer) event.payload().get("seq"));
    record.setKind(kind);
    record.setToolName((String) event.payload().get("tool"));
    record.setArgsJson(toJson(event.payload().get("args")));
    record.setObservationJson(toJson(
        event.payload().get("observation") == null
            ? event.payload().get("preview")
            : event.payload().get("observation")));
    record.setStatus(kind.equals("confirm") ? "AWAITING_CONFIRM" : status);
    record.setRevision(0);
    return record;
  }

  private AgentAuditRecord auditRecord(CurrentSession owner, String taskId, EngineEvent event) {
    Map<String, Object> payload = event.payload();
    AgentAuditRecord record = new AgentAuditRecord();
    record.setId(IdGenerator.randomId("aud"));
    record.setTaskId(taskId);
    record.setUserId(owner.getUserId());
    record.setSource("task");
    record.setToolName((String) payload.get("tool"));
    record.setArgsDigest(toJson(payload.get("args")));
    record.setResultDigest(String.valueOf(payload.get("observation")));
    record.setWriteOperation(Boolean.TRUE.equals(payload.get("write")));
    record.setConfirmed(payload.get("confirmed") == null ? null
        : Boolean.TRUE.equals(payload.get("confirmed")));
    record.setAllowed(!Boolean.FALSE.equals(payload.get("allowed")));
    record.setDetail(null);
    return record;
  }

  private Map<String, Object> toStepView(AgentTaskStepRecord record) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("id", record.getId());
    view.put("seq", record.getSeq());
    view.put("kind", record.getKind());
    view.put("toolName", record.getToolName());
    view.put("status", record.getStatus());
    view.put("argsJson", record.getArgsJson());
    view.put("observationJson", record.getObservationJson());
    view.put("createdAt", record.getCreatedAt());
    return view;
  }

  private String toJson(Object value) {
    try {
      return value == null ? null : objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      return null;
    }
  }

  private void evictOldSessions() {
    if (sessions.size() < MAX_SESSIONS) {
      return;
    }
    sessions.entrySet().stream()
        .filter(entry -> entry.getValue().isTerminal())
        .map(Map.Entry::getKey)
        .limit(Math.max(1, sessions.size() - MAX_SESSIONS + 1))
        .forEach(sessions::remove);
  }
}
