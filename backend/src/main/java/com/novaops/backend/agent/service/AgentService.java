package com.novaops.backend.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.dto.ChatRequest;
import com.novaops.backend.agent.dto.ConversationDetailResponse;
import com.novaops.backend.agent.mapper.AgentMapper;
import com.novaops.backend.agent.model.AgentMessageRecord;
import com.novaops.backend.agent.model.ConversationRecord;
import com.novaops.backend.agent.model.ConversationTurn;
import com.novaops.backend.agent.model.ValidationStatus;
import com.novaops.backend.agent.model.WorkflowResult;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.util.IdGenerator;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentService {
  private final AgentMapper mapper;
  private final AgentWorkflowOrchestrator orchestrator;
  private final ObjectMapper objectMapper;
  private final Executor taskExecutor;
  private final long timeout;

  public AgentService(
      AgentMapper mapper,
      AgentWorkflowOrchestrator orchestrator,
      ObjectMapper objectMapper,
      @Qualifier("agentTaskExecutor") Executor taskExecutor,
      @Value("${app.agent.sse-timeout-ms:120000}") long timeout) {
    this.mapper = mapper;
    this.orchestrator = orchestrator;
    this.objectMapper = objectMapper;
    this.taskExecutor = taskExecutor;
    this.timeout = timeout;
  }

  public SseEmitter chat(CurrentSession session, ChatRequest request) {
    SseEmitter emitter = new SseEmitter(timeout);
    AtomicReference<CompletableFuture<Void>> upstream = new AtomicReference<>();
    emitter.onCompletion(() -> cancel(upstream));
    emitter.onTimeout(() -> {
      cancel(upstream);
      emitter.complete();
    });
    emitter.onError(error -> cancel(upstream));

    ConversationRecord conversation = resolveConversation(session, request);
    List<ConversationTurn> history = recentHistory(mapper.listMessages(conversation.getId()));
    saveMessage(conversation.getId(), "user", request.getContent(), null, null);
    long started = System.currentTimeMillis();

    CompletableFuture<Void> task;
    try {
      task = CompletableFuture.runAsync(() -> {
        try {
          var route = orchestrator.route(request.getContent(), history);
          send(emitter, "route", conversation.getId(), Map.of(
              "route", route.route().name(),
              "reason", route.reason()));
          WorkflowResult result = orchestrator.execute(request.getContent(), history, route);
          sendResult(emitter, conversation.getId(), result, started);
        } catch (Exception ex) {
          sendError(emitter, conversation.getId(), "智能问答服务暂不可用，请稍后重试。");
        }
      }, taskExecutor);
    } catch (RejectedExecutionException ex) {
      sendError(emitter, conversation.getId(), "智能问答请求繁忙，请稍后重试。");
      return emitter;
    }
    upstream.set(task);
    return emitter;
  }

  public List<ConversationRecord> conversations(CurrentSession session) {
    return mapper.listConversations(session.getUserId());
  }

  public ConversationDetailResponse detail(CurrentSession session, String id) {
    ConversationRecord conversation = mapper.findConversation(session.getUserId(), id);
    if (conversation == null) {
      throw new BusinessException(404, "会话不存在");
    }
    return new ConversationDetailResponse(conversation, mapper.listMessages(id));
  }

  private ConversationRecord resolveConversation(CurrentSession session, ChatRequest request) {
    if (request.getConversationId() != null && !request.getConversationId().isBlank()) {
      ConversationRecord existing = mapper.findConversation(session.getUserId(), request.getConversationId());
      if (existing == null) {
        throw new BusinessException(404, "会话不存在");
      }
      return existing;
    }
    ConversationRecord record = new ConversationRecord();
    record.setId(IdGenerator.randomId("conv"));
    record.setUserId(session.getUserId());
    record.setTitle(request.getContent().substring(0, Math.min(40, request.getContent().length())));
    mapper.insertConversation(record);
    return mapper.findConversation(session.getUserId(), record.getId());
  }

  private List<ConversationTurn> recentHistory(List<AgentMessageRecord> messages) {
    int start = Math.max(0, messages.size() - 8);
    return messages.subList(start, messages.size()).stream()
        .map(message -> new ConversationTurn(message.getRole(), message.getContent()))
        .toList();
  }

  private void sendResult(SseEmitter emitter, String conversationId, WorkflowResult result, long started)
      throws IOException {
    sendChunks(emitter, conversationId, result.answer());
    send(emitter, "citation", conversationId, Map.of("citations", result.citations()));
    send(emitter, "evidence", conversationId, Map.of("evidence", result.evidence()));
    send(emitter, "meta", conversationId, Map.of(
        "retrievalExecuted", result.retrievalExecuted(),
        "retrievedCount", result.retrievedCount(),
        "validatedCount", result.validatedCount(),
        "validationStatus", result.validationStatus().name(),
        "validationReason", result.validationReason(),
        "elapsedMs", System.currentTimeMillis() - started));
    saveMessage(
        conversationId,
        "assistant",
        result.answer(),
        result.citations(),
        persistedValidation(result.validationStatus()));
    mapper.touchConversation(conversationId);
    send(emitter, "done", conversationId, Map.of());
    emitter.complete();
  }

  private Boolean persistedValidation(ValidationStatus status) {
    return switch (status) {
      case PASSED -> Boolean.TRUE;
      case FAILED -> Boolean.FALSE;
      default -> null;
    };
  }

  private void sendChunks(SseEmitter emitter, String conversationId, String answer) throws IOException {
    for (int start = 0; start < answer.length(); start += 24) {
      send(emitter, "delta", conversationId,
          Map.of("content", answer.substring(start, Math.min(answer.length(), start + 24))));
    }
  }

  private void send(SseEmitter emitter, String event, String conversationId, Map<String, ?> payload)
      throws IOException {
    java.util.HashMap<String, Object> data = new java.util.HashMap<>(payload);
    data.put("conversationId", conversationId);
    emitter.send(SseEmitter.event().name(event).data(data));
  }

  private void sendError(SseEmitter emitter, String conversationId, String message) {
    try {
      send(emitter, "error", conversationId, Map.of("message", message));
      emitter.complete();
    } catch (IOException ex) {
      emitter.completeWithError(ex);
    }
  }

  private void saveMessage(String conversationId, String role, String content, Object citations, Boolean passed) {
    AgentMessageRecord record = new AgentMessageRecord();
    record.setId(IdGenerator.randomId("msg"));
    record.setConversationId(conversationId);
    record.setRole(role);
    record.setContent(content);
    record.setValidationPassed(passed);
    try {
      record.setCitationsJson(citations == null ? null : objectMapper.writeValueAsString(citations));
    } catch (Exception ignored) {
      record.setCitationsJson(null);
    }
    mapper.insertMessage(record);
  }

  private void cancel(AtomicReference<CompletableFuture<Void>> reference) {
    CompletableFuture<Void> task = reference.get();
    if (task != null && !task.isDone()) {
      task.cancel(true);
    }
  }
}
