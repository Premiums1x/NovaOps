package com.novaops.backend.kb.service;

import com.novaops.backend.kb.dto.KnowledgeBaseMetadataSnapshot;

public interface KbMetadataService {
  KnowledgeBaseMetadataSnapshot snapshot();
}
