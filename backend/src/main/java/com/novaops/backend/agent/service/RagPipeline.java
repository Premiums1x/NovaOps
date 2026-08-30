package com.novaops.backend.agent.service;

import com.novaops.backend.agent.dto.CitationDto;
import com.novaops.backend.agent.model.ChunkRelevance;
import com.novaops.backend.agent.model.ConversationTurn;
import com.novaops.backend.agent.model.GeneratedAnswer;
import com.novaops.backend.agent.model.GroundingDecision;
import com.novaops.backend.agent.model.QueryRoute;
import com.novaops.backend.agent.model.RagExecutionState;
import com.novaops.backend.agent.model.RagPipelineOutcome;
import com.novaops.backend.agent.model.RouteDecision;
import com.novaops.backend.agent.model.ValidationStatus;
import com.novaops.backend.agent.model.WorkflowResult;
import com.novaops.backend.kb.dto.RetrievalChunk;
import com.novaops.backend.kb.dto.RetrievalResult;
import com.novaops.backend.kb.service.KbRetrievalService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RagPipeline {
  private static final String NO_EVIDENCE = "知识库中暂无相关内容，我无法基于可靠资料回答这个问题。";
  private static final String NO_VALIDATED_EVIDENCE = "检索结果中没有通过相关性校验的可靠证据，我无法回答这个问题。";
  private static final String VALIDATION_REFUSAL = "生成的回答未通过知识库依据校验，我无法安全地返回该回答。";

  private final KbRetrievalService retrievalService;
  private final AgentModelGateway modelGateway;
  private final CitationIntegrityValidator citationValidator;
  private final int topK;
  private final double minScore;
  private final double relevanceThreshold;

  public RagPipeline(
      KbRetrievalService retrievalService,
      AgentModelGateway modelGateway,
      @Value("${app.agent.top-k:5}") int topK,
      @Value("${app.agent.min-score:0.55}") double minScore,
      @Value("${app.agent.retrieval-validation-min-score:0.5}") double relevanceThreshold) {
    this.retrievalService = retrievalService;
    this.modelGateway = modelGateway;
    this.citationValidator = new CitationIntegrityValidator();
    this.topK = Math.max(1, topK);
    this.minScore = Math.max(0, Math.min(1, minScore));
    this.relevanceThreshold = Math.max(0, Math.min(1, relevanceThreshold));
  }

  public RagPipelineOutcome execute(String question, List<ConversationTurn> history, RouteDecision route) {
    String retrievalQuery = rewriteOrOriginal(question, history);
    List<RetrievalChunk> retrieved;
    try {
      RetrievalResult result = retrievalService.retrieve(retrievalQuery, topK, minScore);
      retrieved = result == null || result.chunks() == null ? List.of() : List.copyOf(result.chunks());
    } catch (Exception ex) {
      return terminal(route, question, retrievalQuery, List.of(), List.of(), null, false, false,
          "vector_store_unavailable", "知识库检索服务暂不可用，请稍后重试。", ValidationStatus.SERVICE_UNAVAILABLE);
    }
    if (retrieved.isEmpty()) {
      return terminal(route, question, retrievalQuery, retrieved, List.of(), null, false, false,
          "no_retrieved_chunks", NO_EVIDENCE, ValidationStatus.NO_EVIDENCE);
    }

    List<RetrievalChunk> validated;
    try {
      validated = validateAndRerank(retrievalQuery, retrieved);
    } catch (Exception ex) {
      return terminal(route, question, retrievalQuery, retrieved, List.of(), null, false, false,
          "retrieval_validator_unavailable", "检索结果校验服务暂不可用，请稍后重试。",
          ValidationStatus.SERVICE_UNAVAILABLE);
    }
    if (validated.isEmpty()) {
      return terminal(route, question, retrievalQuery, retrieved, validated, null, false, false,
          "no_validated_chunks", NO_VALIDATED_EVIDENCE, ValidationStatus.NO_EVIDENCE);
    }

    String lastFailure = "answer_validation_failed";
    GeneratedAnswer lastAnswer = null;
    boolean lastCitationPassed = false;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        GeneratedAnswer answer = modelGateway.generateRagAnswer(question, validated);
        lastAnswer = answer;
        CitationIntegrityValidator.ValidationResult citation = citationValidator.validate(answer, validated);
        lastCitationPassed = citation.passed();
        if (!citation.passed()) {
          lastFailure = citation.reason();
          continue;
        }
        GroundingDecision grounding = modelGateway.validateGrounding(answer, citation.citedChunks());
        if (grounding == null || !grounding.supported()) {
          lastFailure = grounding == null ? "grounding_validator_empty" : grounding.reason();
          continue;
        }
        List<CitationDto> evidenceDtos = toDtos(validated);
        List<CitationDto> citationDtos = mapCitations(citation.citedChunks(), validated);
        RagExecutionState state = new RagExecutionState(
            question, retrievalQuery, true, retrieved, validated, answer, true, true, "");
        WorkflowResult response = new WorkflowResult(
            QueryRoute.RAG, route.reason(), answer.answer(), citationDtos, evidenceDtos, true,
            retrieved.size(), validated.size(), ValidationStatus.PASSED, "grounding_and_citation_integrity_passed");
        return new RagPipelineOutcome(response, state);
      } catch (Exception ex) {
        lastFailure = "answer_or_grounding_model_failure";
      }
    }
    return terminal(route, question, retrievalQuery, retrieved, validated, lastAnswer, false, lastCitationPassed,
        lastFailure, VALIDATION_REFUSAL, ValidationStatus.FAILED);
  }

  private String rewriteOrOriginal(String question, List<ConversationTurn> history) {
    try {
      String rewritten = modelGateway.rewrite(question, history);
      return rewritten == null || rewritten.isBlank() ? question : rewritten.trim();
    } catch (Exception ex) {
      return question;
    }
  }

  private List<RetrievalChunk> validateAndRerank(String query, List<RetrievalChunk> retrieved) {
    List<ChunkRelevance> decisions = modelGateway.validateRetrieval(query, retrieved);
    Map<String, ChunkRelevance> allowedDecisions = new HashMap<>();
    Map<String, RetrievalChunk> retrievedById = new LinkedHashMap<>();
    for (RetrievalChunk chunk : retrieved) {
      if (chunk != null && chunk.chunkId() != null && !chunk.chunkId().isBlank()) {
        retrievedById.putIfAbsent(chunk.chunkId(), chunk);
      }
    }
    if (decisions != null) {
      for (ChunkRelevance decision : decisions) {
        if (decision != null && retrievedById.containsKey(decision.chunkId())) {
          allowedDecisions.merge(decision.chunkId(), decision,
              (left, right) -> left.score() >= right.score() ? left : right);
        }
      }
    }
    return retrievedById.values().stream()
        .filter(chunk -> {
          ChunkRelevance decision = allowedDecisions.get(chunk.chunkId());
          return decision != null && decision.relevant() && decision.score() >= relevanceThreshold;
        })
        .sorted(Comparator.comparingDouble(
            (RetrievalChunk chunk) -> allowedDecisions.get(chunk.chunkId()).score()).reversed())
        .toList();
  }

  private RagPipelineOutcome terminal(
      RouteDecision route,
      String question,
      String retrievalQuery,
      List<RetrievalChunk> retrieved,
      List<RetrievalChunk> validated,
      GeneratedAnswer answer,
      boolean groundingPassed,
      boolean citationPassed,
      String reason,
      String responseText,
      ValidationStatus status) {
    List<CitationDto> evidenceDtos = toDtos(validated);
    RagExecutionState state = new RagExecutionState(
        question, retrievalQuery, true, retrieved, validated, answer, groundingPassed, citationPassed, reason);
    WorkflowResult response = new WorkflowResult(
        QueryRoute.RAG, route.reason(), responseText, List.of(), evidenceDtos, true,
        retrieved.size(), validated.size(), status, reason);
    return new RagPipelineOutcome(response, state);
  }

  private List<CitationDto> toDtos(List<RetrievalChunk> chunks) {
    return java.util.stream.IntStream.range(0, chunks.size())
        .mapToObj(index -> toDto(index + 1, chunks.get(index)))
        .toList();
  }

  private List<CitationDto> mapCitations(List<RetrievalChunk> cited, List<RetrievalChunk> validated) {
    Map<String, Integer> indexes = new HashMap<>();
    for (int index = 0; index < validated.size(); index++) {
      indexes.put(validated.get(index).chunkId(), index + 1);
    }
    return cited.stream().map(chunk -> toDto(indexes.get(chunk.chunkId()), chunk)).toList();
  }

  private CitationDto toDto(int index, RetrievalChunk chunk) {
    return new CitationDto(index, chunk.documentId(), chunk.documentName(), chunk.chunkId(), chunk.content(), chunk.score());
  }
}
