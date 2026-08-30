package com.novaops.backend.agent.model;

public record ChunkRelevance(String chunkId, boolean relevant, double score, String reason) {
  public ChunkRelevance {
    chunkId = chunkId == null ? "" : chunkId.trim();
    score = Math.max(0, Math.min(1, score));
    reason = reason == null ? "" : reason.trim();
  }
}
