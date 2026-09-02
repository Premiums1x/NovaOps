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
        try {
          engine.run(state, event -> {
            if ("result".equals(event.type()) || "error".equals(event.type())) {
              agentSession.markTerminal();
            }
            persist(owner, state.taskId(), event);
            agentSession.record(event);
          });
        } catch (Exception ex) {
          // 引擎 try/catch 之外逃逸的异常兜底：标记终态并落 FAILED，
          // 终态写入幂等（finishTask 带 status 守卫），不会覆盖已写入的取消/完成态
          log.error("agent task execution failed unexpectedly: taskId={}", state.taskId(), ex);
          agentSession.markTerminal();
          EngineEvent errorEvent = EngineEvent.of(
              "error", state.nextGlobalSeq(), Map.of("message", "任务执行异常，请重新发起"));
          persist(owner, state.taskId(), errorEvent);
          agentSession.record(errorEvent);
        }
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
    List<AgentTaskStepRecord> stepRecords = taskMapper.listSteps(taskId);
    List<Map<String, Object>> steps = stepRecords.stream()
        .map(this::toStepView)
        .toList();
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("task", task);
    view.put("steps", steps);
    // 挂起确认的确认令牌只存在内存会话里，这里显式回传让前端刷新后仍能恢复确认弹窗
    Map<String, Object> pending = pendingConfirmationView(taskId, stepRecords);
    if (pending != null) {
      view.put("pendingConfirmation", pending);
    }
    return view;
  }

  /** 挂起确认的恢复信息；preview 从步骤表 confirm 记录的 observationJson 恢复。 */
  private Map<String, Object> pendingConfirmationView(
      String taskId, List<AgentTaskStepRecord> stepRecords) {
    AgentTaskSession agentSession = sessions.get(taskId);
    if (agentSession == null || agentSession.isTerminal()) {
      return null;
    }
    EngineState state = agentSession.state();
    String confirmationId = state.pendingConfirmationId();
    var pendingStep = state.pendingStep();
    if (confirmationId == null || pendingStep == null) {
      return null;
    }
    // 同一任务同时至多一个挂起确认，取最后一条 confirm 记录（listSteps 按 seq 排序）
    String previewJson = null;
    for (AgentTaskStepRecord record : stepRecords) {
      if ("confirm".equals(record.getKind()) && record.getObservationJson() != null) {
        previewJson = record.getObservationJson();
      }
    }
    Map<String, Object> pending = new LinkedHashMap<>();
    pending.put("confirmationId", confirmationId);
    pending.put("tool", pendingStep.tool());
    pending.put("title", toolRegistry.find(pendingStep.tool())
        .map(handle -> handle.descriptor().title())
        .orElse(pendingStep.tool()));
    pending.put("why", pendingStep.why() == null ? "" : pendingStep.why());
    pending.put("args", pendingStep.args() == null ? Map.of() : pendingStep.args());
    pending.put("preview", parsePreview(previewJson));
    return pending;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parsePreview(String previewJson) {
    if (previewJson == null || previewJson.isBlank()) {
      return Map.of();
    }
    try {
      Object parsed = objectMapper.readValue(previewJson, Object.class);
      return parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
    } catch (Exception ex) {
      return Map.of();
    }
  }

  public List<AgentTaskRecord> list(CurrentSession session) {
    return taskMapper.listTasks(session.getUserId(), 50);
  }

  /** 任务中心的审计明细：先校验任务归属（沿用 detail 的 userId 过滤），再返回审计记录。 */
  public List<AgentAuditRecord> audits(CurrentSession session, String taskId) {
    if (taskMapper.findTask(taskId, session.getUserId()) == null) {
      throw new BusinessException(404, "任务不存在");
    }
    return auditMapper.listAuditsByTask(taskId);
  }

  /** 任务统计（统计卡片）：任务总数/状态分布/平均步数/写操作与确认次数。 */
  public Map<String, Object> stats(CurrentSession session) {
    String userId = session.getUserId();
    Map<String, Long> byStatus = new LinkedHashMap<>();
    long total = 0;
    long done = 0;
    for (Map<String, Object> row : taskMapper.countTasksByStatus(userId)) {
      String status = String.valueOf(valueIgnoreCase(row, "status"));
      long cnt = ((Number) valueIgnoreCase(row, "cnt")).longValue();
      byStatus.put(status, cnt);
      total += cnt;
      if ("DONE".equals(status)) {
        done = cnt;
      }
    }
    Double avgSteps = taskMapper.avgToolStepsPerTask(userId);
    Map<String, Object> writeStats = auditMapper.writeAuditStats(userId);
    long writeOperations = writeStats == null ? 0
        : (long) toNumber(valueIgnoreCase(writeStats, "writeOperations"));
    long confirmedOperations = writeStats == null ? 0
        : (long) toNumber(valueIgnoreCase(writeStats, "confirmedOperations"));

    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("total", total);
    stats.put("byStatus", byStatus);
    stats.put("successRate", total == 0 ? 0.0 : Math.round(done * 100.0 / total) / 100.0);
    stats.put("avgSteps", avgSteps == null ? 0 : Math.round(avgSteps * 10.0) / 10.0);
    stats.put("writeOperations", writeOperations);
    stats.put("confirmedOperations", confirmedOperations);
    return stats;
  }

  private Object valueIgnoreCase(Map<String, Object> row, String key) {
    return row.entrySet().stream()
        .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(key))
        .findFirst()
        .map(Map.Entry::getValue)
        .orElse(null);
  }

  private long toNumber(Object value) {
    return value instanceof Number number ? number.longValue() : 0;
  }

  /**
   * TTL 清扫：把超过阈值仍处非终态、且内存会话已不存在的任务置为 FAILED
   * （服务重启后 RUNNING/AWAITING_CONFIRM 记录的兜底回收）。返回清扫数量。
   */
  public int sweepStaleTasks(java.time.Duration staleThreshold) {
    java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minus(staleThreshold);
    List<String> staleIds = taskMapper.findStaleTaskIds(cutoff);
    int swept = 0;
    for (String taskId : staleIds) {
      // 有活跃内存会话的任务（运行中或挂起确认）不误杀
      if (sessions.containsKey(taskId)) {
        continue;
      }
      taskMapper.finishTask(taskId, "FAILED", null, "任务会话已失效，请重新发起");
      swept++;
    }
    return swept;
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

  /** 仅供同包测试与清理任务读取内存会话。 */
  AgentTaskSession sessionIfPresent(String taskId) {
    return sessions.get(taskId);
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
    Object observation = event.payload().get("observation") == null
        ? event.payload().get("preview")
        : event.payload().get("observation");
    // 工具/总结步骤把事件时间戳包进 observationJson（{"at":..,"observation":..}），不改表结构；
    // confirm 步骤保持裸 preview（挂起恢复弹窗依赖该格式）
    if ("confirm".equals(kind)) {
      record.setObservationJson(toJson(observation));
    } else {
      record.setObservationJson(toJson(Map.of("at", event.at(), "observation", observation == null ? Map.of() : observation)));
    }
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
    // 先清终态会话腾位；若仍达上限（全部在跑/挂起），告警放行，
    // 孤儿会话由 AgentTaskCleanupTask 的 TTL 清扫兜底回收
    sessions.entrySet().removeIf(entry -> entry.getValue().isTerminal());
    if (sessions.size() >= MAX_SESSIONS) {
      log.warn("agent task sessions at capacity ({}), 全部为非终态会话，等待 TTL 清扫回收", MAX_SESSIONS);
    }
  }
}
