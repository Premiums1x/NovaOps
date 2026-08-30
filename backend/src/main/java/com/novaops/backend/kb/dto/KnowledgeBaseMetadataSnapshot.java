package com.novaops.backend.kb.dto;

import java.util.List;

public record KnowledgeBaseMetadataSnapshot(
    long totalDocuments,
    long readyDocuments,
    boolean truncated,
    List<KnowledgeBaseMetadataDocument> documents) {
  public KnowledgeBaseMetadataSnapshot {
    documents = documents == null ? List.of() : List.copyOf(documents);
  }
}
