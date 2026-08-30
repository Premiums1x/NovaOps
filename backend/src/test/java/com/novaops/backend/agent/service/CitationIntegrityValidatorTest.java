package com.novaops.backend.agent.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.novaops.backend.agent.model.GeneratedAnswer;
import com.novaops.backend.kb.dto.RetrievalChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class CitationIntegrityValidatorTest {
  private final CitationIntegrityValidator validator = new CitationIntegrityValidator();
  private final List<RetrievalChunk> evidence = List.of(
      new RetrievalChunk("chunk-12", "doc-1", "Guide.md", "pnpm add package", 0.86));

  @Test
  void acceptsOnlyIdsFromCurrentEvidenceSet() {
    assertTrue(validator.validate(new GeneratedAnswer("使用 pnpm 安装", List.of("chunk-12")), evidence).passed());
    assertFalse(validator.validate(new GeneratedAnswer("使用 pnpm 安装", List.of("chunk-999")), evidence).passed());
  }

  @Test
  void rejectsAnswersWithoutEvidenceBinding() {
    assertFalse(validator.validate(new GeneratedAnswer("使用 pnpm 安装", List.of()), evidence).passed());
    assertFalse(validator.validate(new GeneratedAnswer("", List.of("chunk-12")), evidence).passed());
  }
}
