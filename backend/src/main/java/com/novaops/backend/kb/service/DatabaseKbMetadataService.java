package com.novaops.backend.kb.service;

import com.novaops.backend.kb.dto.KnowledgeBaseMetadataDocument;
import com.novaops.backend.kb.dto.KnowledgeBaseMetadataSnapshot;
import com.novaops.backend.kb.mapper.KbMapper;
import com.novaops.backend.kb.model.KbDocumentRecord;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DatabaseKbMetadataService implements KbMetadataService {
  private final KbMapper mapper;
  private final int maxDocuments;

  public DatabaseKbMetadataService(
      KbMapper mapper,
      @Value("${app.agent.metadata-max-documents:200}") int maxDocuments) {
    this.mapper = mapper;
    this.maxDocuments = Math.max(1, maxDocuments);
  }

  @Override
  public KnowledgeBaseMetadataSnapshot snapshot() {
    long total = mapper.countDocuments(null, null, null);
    long ready = mapper.countDocuments(null, null, "READY");
    List<KnowledgeBaseMetadataDocument> documents = mapper
        .listDocuments(null, null, null, 0, maxDocuments)
        .stream()
        .map(this::toMetadata)
        .toList();
    return new KnowledgeBaseMetadataSnapshot(total, ready, total > documents.size(), documents);
  }

  private KnowledgeBaseMetadataDocument toMetadata(KbDocumentRecord document) {
    return new KnowledgeBaseMetadataDocument(
        document.getId(),
        document.getTitle(),
        document.getFileName(),
        document.getFileType(),
        document.getStatus(),
        document.getChunkCount() == null ? 0 : document.getChunkCount(),
        document.getUpdatedAt());
  }
}
