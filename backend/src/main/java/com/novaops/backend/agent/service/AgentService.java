package com.novaops.backend.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.dto.ChatRequest;
import com.novaops.backend.agent.dto.CitationDto;
import com.novaops.backend.agent.dto.ConversationDetailResponse;
import com.novaops.backend.agent.mapper.AgentMapper;
import com.novaops.backend.agent.model.AgentMessageRecord;
import com.novaops.backend.agent.model.ConversationRecord;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.util.IdGenerator;
import com.novaops.backend.kb.dto.RetrievalChunk;
import com.novaops.backend.kb.dto.RetrievalResult;
import com.novaops.backend.kb.service.KbRetrievalService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@Service
public class AgentService {

  private static final String REFUSAL = "知识库中暂无相关内容，我无法基于可靠资料回答这个问题。";
  private static final String SYSTEM = "你是 NovaOps 企业知识助手。只能依据提供的知识库资料回答；每个事实论断必须标注对应引用编号如 [1]；不得使用资料外知识；资料不足时明确说不知道。";

  private final AgentMapper mapper;
  private final KbRetrievalService retrievalService;
  private final ChatClient chatClient;
  private final CitationValidator validator;
  private final AgentPlanner planner;
  private final ObjectMapper objectMapper;
  private final long timeout;
  private final int topK;
  private final double minScore;

  public AgentService(
      AgentMapper mapper,
      KbRetrievalService retrievalService,
      ChatClient.Builder builder,
      CitationValidator validator,
      AgentPlanner planner,
      ObjectMapper objectMapper,
      @Value("${app.agent.sse-timeout-ms:120000}") long timeout,
      @Value("${app.agent.top-k:5}") int topK,
      @Value("${app.agent.min-score:0.55}") double minScore) {
    this.mapper = mapper;
    this.retrievalService = retrievalService;
    this.chatClient = builder.build();
    this.validator = validator;
    this.planner = planner;
    this.objectMapper = objectMapper;
    this.timeout = timeout;
    this.topK = topK;
    this.minScore = minScore;
  }

  public SseEmitter chat(CurrentSession session, ChatRequest request) {
    SseEmitter emitter = new SseEmitter(timeout);
    AtomicReference<Disposable> upstream = new AtomicReference<>();
    emitter.onCompletion(() -> dispose(upstream));
    emitter.onTimeout(() -> {
      dispose(upstream);
      emitter.complete();
    });
    emitter.onError(error -> dispose(upstream));

    ConversationRecord conversation = resolveConversation(session, request);
    saveMessage(conversation.getId(), "user", request.getContent(), null, null);
    AgentPlanDto plan = planner.plan(request.getContent());
    String searchQuery = plan.steps().stream()
        .filter(step -> "search_kb".equals(step.action()))
        .map(AgentPlanStepDto::query)
        .filter(query -> query != null && !query.isBlank())
        .findFirst()
        .orElse(request.getContent());
    try {
      send(emitter, "plan", conversation.getId(), Map.of("steps", plan.steps()));
      sendStep(emitter, conversation.getId(), "search_kb", "running", Map.of());
    } catch (IOException exception) {
      emitter.completeWithError(exception);
      return emitter;
    }

    RetrievalResult retrieval;
    try {
      retrieval = retrievalService.retrieve(searchQuery, topK, minScore);
    } catch (Exception exception) {
      trySendStep(emitter, conversation.getId(), "search_kb", "failed",
          Map.of("message", "知识库服务暂不可用"));
      completeFixed(emitter, conversation.getId(), "知识库服务暂不可用，请稍后重试。", false,
          "vector_store_unavailable", List.of());
      return emitter;
    }

    try {
      sendStep(emitter, conversation.getId(), "search_kb", "done",
          Map.of("query", searchQuery, "count", retrieval.chunks().size(),
              "topChunks", summarizeChunks(retrieval.chunks())));
    } catch (IOException exception) {
      emitter.completeWithError(exception);
      return emitter;
    }
    if (retrieval.isEmpty()) {
      completeFixed(emitter, conversation.getId(), REFUSAL, true, "no_reliable_context", List.of());
      return emitter;
    }

    String prompt = buildPrompt(request.getContent(), retrieval.chunks());
    StringBuilder buffer = new StringBuilder();
    long started = System.currentTimeMillis();
    try {
      sendStep(emitter, conversation.getId(), "answer", "running", Map.of());
    } catch (IOException exception) {
      emitter.completeWithError(exception);
      return emitter;
    }
    Disposable disposable = chatClient.prompt()
        .system(SYSTEM)
        .user(prompt)
        .stream()
        .content()
        .subscribe(
            buffer::append,
            error -> {
              trySendStep(emitter, conversation.getId(), "answer", "failed",
                  Map.of("message", "模型服务暂不可用"));
              sendError(emitter, conversation.getId(), "模型服务暂不可用，请稍后重试。");
            },
            () -> finishGenerated(
                emitter,
                conversation.getId(),
                prompt,
                buffer.toString(),
                retrieval.chunks(),
                started));
    upstream.set(disposable);
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

  private String buildPrompt(String question, List<RetrievalChunk> chunks) {
    StringBuilder context = new StringBuilder("知识库资料：\n");
    for (int i = 0; i < chunks.size(); i++) {
      context.append('[').append(i + 1).append("] 来源：")
          .append(chunks.get(i).documentName()).append('\n')
          .append(chunks.get(i).content()).append("\n\n");
    }
    return context.append("用户问题：").append(question).toString();
  }

  private void finishGenerated(
      SseEmitter emitter,
      String conversationId,
      String prompt,
      String initialAnswer,
      List<RetrievalChunk> chunks,
      long started) {
    try {
      sendStep(emitter, conversationId, "answer", "done",
          Map.of("characterCount", initialAnswer.length()));
      sendStep(emitter, conversationId, "validate", "running", Map.of());
      String answer = initialAnswer;
      CitationValidator.ValidationResult validation = validator.validate(answer, chunks);
      if (!validation.passed()) {
        String retry = chatClient.prompt()
            .system(SYSTEM + " 上一次回答未通过引用校验。请重新回答并确保每个论断引用真实编号。")
            .user(prompt)
            .call()
            .content();
        CitationValidator.ValidationResult second = validator.validate(retry, chunks);
        answer = second.passed() ? retry : retry + "\n\n⚠ 该回答未通过知识库依据校验，仅供参考";
        validation = second;
      }
      List<CitationDto> citations = validation.citationIndexes().stream()
          .sorted()
          .map(index -> {
            RetrievalChunk chunk = chunks.get(index - 1);
            return new CitationDto(index, chunk.documentId(), chunk.documentName(), chunk.chunkId(),
                chunk.content(), chunk.score());
          })
          .toList();
      sendStep(emitter, conversationId, "validate", "done",
          Map.of("passed", validation.passed(), "reason", validation.reason()));
      sendChunks(emitter, conversationId, answer);
      send(emitter, "citation", conversationId, Map.of("citations", citations));
      send(emitter, "meta", conversationId,
          Map.of("validationPassed", validation.passed(), "validationReason", validation.reason(),
              "elapsedMs", System.currentTimeMillis() - started));
      saveMessage(conversationId, "assistant", answer, citations, validation.passed());
      mapper.touchConversation(conversationId);
      send(emitter, "done", conversationId, Map.of());
      emitter.complete();
    } catch (Exception exception) {
      trySendStep(emitter, conversationId, "validate", "failed",
          Map.of("message", "回答校验失败"));
      sendError(emitter, conversationId, "回答校验失败，请稍后重试。");
    }
  }

  private void completeFixed(
      SseEmitter emitter,
      String conversationId,
      String answer,
      boolean validated,
      String reason,
      List<CitationDto> citations) {
    try {
      sendStep(emitter, conversationId, "answer", "running", Map.of());
      sendStep(emitter, conversationId, "answer", "done", Map.of("characterCount", answer.length()));
      sendStep(emitter, conversationId, "validate", "running", Map.of());
      sendStep(emitter, conversationId, "validate", "done",
          Map.of("passed", validated, "reason", reason));
      sendChunks(emitter, conversationId, answer);
      send(emitter, "citation", conversationId, Map.of("citations", citations));
      send(emitter, "meta", conversationId,
          Map.of("validationPassed", validated, "validationReason", reason));
      saveMessage(conversationId, "assistant", answer, citations, validated);
      mapper.touchConversation(conversationId);
      send(emitter, "done", conversationId, Map.of());
      emitter.complete();
    } catch (Exception exception) {
      emitter.completeWithError(exception);
    }
  }

  private List<Map<String, Object>> summarizeChunks(List<RetrievalChunk> chunks) {
    List<Map<String, Object>> summaries = new ArrayList<>();
    for (RetrievalChunk chunk : chunks) {
      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("documentName", chunk.documentName());
      summary.put("score", chunk.score());
      summaries.add(summary);
    }
    return summaries;
  }

  private void sendChunks(SseEmitter emitter, String conversationId, String answer) throws IOException {
    for (int start = 0; start < answer.length(); start += 24) {
      send(emitter, "delta", conversationId,
          Map.of("content", answer.substring(start, Math.min(answer.length(), start + 24))));
    }
  }

  private void sendStep(
      SseEmitter emitter,
      String conversationId,
      String action,
      String status,
      Map<String, ?> payload) throws IOException {
    Map<String, Object> step = new HashMap<>();
    step.put("action", action);
    step.put("status", status);
    if (!payload.isEmpty()) {
      step.put("payload", payload);
    }
    send(emitter, "step", conversationId, step);
  }

  private void trySendStep(
      SseEmitter emitter,
      String conversationId,
      String action,
      String status,
      Map<String, ?> payload) {
    try {
      sendStep(emitter, conversationId, action, status, payload);
    } catch (IOException ignored) {
      // The stream is already unusable; the completion path releases it.
    }
  }

  private void send(
      SseEmitter emitter,
      String event,
      String conversationId,
      Map<String, ?> payload) throws IOException {
    HashMap<String, Object> data = new HashMap<>(payload);
    data.put("conversationId", conversationId);
    emitter.send(SseEmitter.event().name(event).data(data));
  }

  private void sendError(SseEmitter emitter, String conversationId, String message) {
    try {
      send(emitter, "error", conversationId, Map.of("message", message));
      emitter.complete();
    } catch (IOException exception) {
      emitter.completeWithError(exception);
    }
  }

  private void saveMessage(
      String conversationId,
      String role,
      String content,
      Object citations,
      Boolean passed) {
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

  private void dispose(AtomicReference<Disposable> reference) {
    Disposable disposable = reference.get();
    if (disposable != null && !disposable.isDisposed()) {
      disposable.dispose();
    }
  }
}
