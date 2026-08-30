package com.novaops.backend.agent.service;

import com.novaops.backend.agent.model.GeneratedAnswer;
import com.novaops.backend.kb.dto.RetrievalChunk;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CitationIntegrityValidator {
  public record ValidationResult(boolean passed, String reason, List<RetrievalChunk> citedChunks) {}

  public ValidationResult validate(GeneratedAnswer answer, List<RetrievalChunk> allowedEvidence) {
    if (answer == null || answer.answer().isBlank()) {
      return new ValidationResult(false, "回答正文为空", List.of());
    }
    if (answer.citationChunkIds().isEmpty()) {
      return new ValidationResult(false, "回答没有绑定真实 chunkId", List.of());
    }
    Map<String, RetrievalChunk> allowedById = new LinkedHashMap<>();
    for (RetrievalChunk chunk : allowedEvidence) {
      if (chunk != null && chunk.chunkId() != null && !chunk.chunkId().isBlank()) {
        allowedById.putIfAbsent(chunk.chunkId(), chunk);
      }
    }
    for (String citationId : answer.citationChunkIds()) {
      if (!allowedById.containsKey(citationId)) {
        return new ValidationResult(false, "回答包含本次有效证据集之外的 chunkId: " + citationId, List.of());
      }
    }
    List<RetrievalChunk> cited = answer.citationChunkIds().stream().map(allowedById::get).toList();
    return new ValidationResult(true, "引用 ID 完整性校验通过", cited);
  }
}
