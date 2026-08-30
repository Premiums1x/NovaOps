package com.novaops.backend.agent.model;

import java.util.List;

public record GeneratedAnswer(String answer, List<String> citationChunkIds) {
  public GeneratedAnswer {
    answer = answer == null ? "" : answer.trim();
    citationChunkIds = citationChunkIds == null
        ? List.of()
        : citationChunkIds.stream().filter(id -> id != null && !id.isBlank()).map(String::trim).distinct().toList();
  }
}
