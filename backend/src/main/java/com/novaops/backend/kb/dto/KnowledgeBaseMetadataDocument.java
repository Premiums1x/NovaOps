package com.novaops.backend.kb.dto;

import java.time.LocalDateTime;

public record KnowledgeBaseMetadataDocument(
    String documentId,
    String title,
    String fileName,
    String fileType,
    String status,
    int chunkCount,
    LocalDateTime updatedAt) {}
