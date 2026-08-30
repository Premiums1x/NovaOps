package com.novaops.backend.agent.model;

import com.novaops.backend.kb.dto.RetrievalChunk;
import java.util.List;

public record RagExecutionState(
    String originalQuery,
    String retrievalQuery,
    boolean retrievalExecuted,
    List<RetrievalChunk> retrievedChunks,
    List<RetrievalChunk> validatedChunks,
    GeneratedAnswer generatedAnswer,
    boolean groundingPassed,
    boolean citationIntegrityPassed,
    String failureReason) {
  public RagExecutionState {
    retrievedChunks = retrievedChunks == null ? List.of() : List.copyOf(retrievedChunks);
    validatedChunks = validatedChunks == null ? List.of() : List.copyOf(validatedChunks);
    failureReason = failureReason == null ? "" : failureReason;
  }
}
