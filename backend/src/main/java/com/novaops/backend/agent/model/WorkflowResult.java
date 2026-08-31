package com.novaops.backend.agent.model;

import com.novaops.backend.agent.dto.CitationDto;
import com.novaops.backend.kb.dto.RetrievalChunk;
import java.util.List;

public record WorkflowResult(
    QueryRoute route,
    String routeReason,
    String answer,
    List<CitationDto> citations,
    List<CitationDto> evidence,
    boolean retrievalExecuted,
    int retrievedCount,
    int validatedCount,
    ValidationStatus validationStatus,
    String validationReason,
    List<RetrievalChunk> retrievedChunks,
    List<RetrievalChunk> validatedChunks) {
  public WorkflowResult {
    routeReason = routeReason == null ? "" : routeReason;
    answer = answer == null ? "" : answer;
    citations = citations == null ? List.of() : List.copyOf(citations);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    validationReason = validationReason == null ? "" : validationReason;
    retrievedChunks = retrievedChunks == null ? List.of() : List.copyOf(retrievedChunks);
    validatedChunks = validatedChunks == null ? List.of() : List.copyOf(validatedChunks);
  }

  public WorkflowResult(
      QueryRoute route,
      String routeReason,
      String answer,
      List<CitationDto> citations,
      List<CitationDto> evidence,
      boolean retrievalExecuted,
      int retrievedCount,
      int validatedCount,
      ValidationStatus validationStatus,
      String validationReason) {
    this(route, routeReason, answer, citations, evidence, retrievalExecuted, retrievedCount, validatedCount,
        validationStatus, validationReason, List.of(), List.of());
  }
}
