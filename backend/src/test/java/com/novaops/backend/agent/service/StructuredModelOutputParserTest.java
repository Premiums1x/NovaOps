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
  void parsesControlledRouteSchemaAndRestrictsRouteEnum() {
    var decision = parser.parseRoute("""
        {"version":"1","route":"metadata","intent":"knowledge_overview","confidence":0.98,
         "reasonCode":"metadata_overview","semanticQuery":"","metadataOperation":"overview",
         "documentFilter":"","fileTypeFilter":"","statusFilter":"","topK":5,"reason":"总览"}
        """);
    assertEquals(QueryRoute.METADATA, decision.route());
    assertEquals("总览", decision.reason());
    assertThrows(IllegalArgumentException.class,
        () -> parser.parseRoute("""
            {"version":"1","route":"TOOLS","intent":"tool","confidence":1,"reasonCode":"bad",
             "semanticQuery":"","metadataOperation":"","documentFilter":"","fileTypeFilter":"",
             "statusFilter":"","topK":5,"reason":"越权"}
            """));
  }

  @Test
  void rejectsUnknownRouteFieldsAndInvalidConfidence() {
    String base = """
        {"version":"1","route":"CHAT","intent":"greeting","confidence":%s,"reasonCode":"greeting",
         "semanticQuery":"","metadataOperation":"","documentFilter":"","fileTypeFilter":"",
         "statusFilter":"","topK":5,"reason":"hello"%s}
        """;
    assertThrows(IllegalArgumentException.class, () -> parser.parseRoute(base.formatted("2", "")));
    assertThrows(IllegalArgumentException.class, () -> parser.parseRoute(base.formatted("1", ",\"extra\":true")));
    assertThrows(IllegalArgumentException.class, () -> parser.parseRoute("```json\n" + base.formatted("1", "") + "\n```"));
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
