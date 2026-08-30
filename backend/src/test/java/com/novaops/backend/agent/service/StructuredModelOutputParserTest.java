package com.novaops.backend.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.model.QueryRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StructuredModelOutputParserTest {
  private StructuredModelOutputParser parser;

  @BeforeEach
  void setUp() {
    parser = new StructuredModelOutputParser(new ObjectMapper());
  }

  @Test
  void parsesJsonInsideMarkdownFenceAndRestrictsRouteEnum() {
    var decision = parser.parseRoute("```json\n{\"route\":\"metadata\",\"reason\":\"总览\"}\n```");
    assertEquals(QueryRoute.METADATA, decision.route());
    assertEquals("总览", decision.reason());
    assertThrows(IllegalArgumentException.class,
        () -> parser.parseRoute("{\"route\":\"TOOLS\",\"reason\":\"越权\"}"));
  }

  @Test
  void parsesAndNormalizesStructuredAnswer() {
    var answer = parser.parseAnswer("{\"answer\":\"使用 pnpm 安装\",\"citationChunkIds\":[\"c1\",\"c1\",\"\"]}");
    assertEquals("使用 pnpm 安装", answer.answer());
    assertEquals(java.util.List.of("c1"), answer.citationChunkIds());
  }

  @Test
  void rejectsIncompleteStructures() {
    assertThrows(IllegalArgumentException.class, () -> parser.parseAnswer("{\"answer\":\"x\"}"));
    assertThrows(IllegalArgumentException.class, () -> parser.parseRelevance("{\"items\":{}}"));
    assertThrows(IllegalArgumentException.class, () -> parser.parseGrounding("not json"));
  }

  @Test
  void clampsRelevanceScores() {
    var result = parser.parseRelevance("{\"items\":[{\"chunkId\":\"c1\",\"relevant\":true,\"score\":9,\"reason\":\"ok\"}]}");
    assertEquals(1, result.size());
    assertTrue(result.get(0).relevant());
    assertEquals(1, result.get(0).score());
  }
}
