package com.novaops.backend.agent.model;

import com.novaops.backend.agent.dto.CitationDto;
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
    String validationReason) {
  public WorkflowResult {
    routeReason = routeReason == null ? "" : routeReason;
    answer = answer == null ? "" : answer;
    citations = citations == null ? List.of() : List.copyOf(citations);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    validationReason = validationReason == null ? "" : validationReason;
  }
}
