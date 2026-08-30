package com.novaops.backend.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novaops.backend.agent.model.ChunkRelevance;
import com.novaops.backend.agent.model.ConversationTurn;
import com.novaops.backend.agent.model.GeneratedAnswer;
import com.novaops.backend.agent.model.GroundingDecision;
import com.novaops.backend.agent.model.QueryRoute;
import com.novaops.backend.agent.model.RouteDecision;
import com.novaops.backend.agent.model.ValidationStatus;
import com.novaops.backend.kb.dto.RetrievalChunk;
import com.novaops.backend.kb.dto.RetrievalResult;
import com.novaops.backend.kb.service.KbRetrievalService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RagPipelineTest {
  private KbRetrievalService retriever;
  private AgentModelGateway gateway;
  private RagPipeline pipeline;
  private RouteDecision route;
  private RetrievalChunk chunk;

  @BeforeEach
  void setUp() {
    retriever = Mockito.mock(KbRetrievalService.class);
    gateway = Mockito.mock(AgentModelGateway.class);
    pipeline = new RagPipeline(retriever, gateway, 5, 0.55, 0.5);
    route = new RouteDecision(QueryRoute.RAG, "具体知识问题");
    chunk = new RetrievalChunk("chunk-12", "doc-1", "Guide.md", "pnpm add vue-element-plus-x", 0.86);
    when(gateway.rewrite(anyString(), anyList())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void zeroRetrievedChunksNeverCallsAnswerGenerator() {
    when(retriever.retrieve(anyString(), anyInt(), anyDouble()))
        .thenReturn(new RetrievalResult(List.of()));

    var outcome = pipeline.execute("怎么安装？", List.of(), route);

    assertEquals(ValidationStatus.NO_EVIDENCE, outcome.response().validationStatus());
    assertTrue(outcome.state().retrievalExecuted());
    assertTrue(outcome.state().retrievedChunks().isEmpty());
    verify(gateway, never()).generateRagAnswer(anyString(), anyList());
  }

  @Test
  void successfulAnswerUsesOriginalRetrieverChunkAsEvidence() {
    when(retriever.retrieve(anyString(), anyInt(), anyDouble()))
        .thenReturn(new RetrievalResult(List.of(chunk)));
    when(gateway.validateRetrieval(anyString(), anyList()))
        .thenReturn(List.of(new ChunkRelevance("chunk-12", true, 0.93, "直接回答安装方式")));
    when(gateway.generateRagAnswer(anyString(), anyList()))
        .thenReturn(new GeneratedAnswer("使用 pnpm 安装。", List.of("chunk-12")));
    when(gateway.validateGrounding(any(), anyList()))
        .thenReturn(new GroundingDecision(true, "完全支持", List.of()));

    var outcome = pipeline.execute("怎么安装？", List.of(), route);

    assertEquals(ValidationStatus.PASSED, outcome.response().validationStatus());
    assertEquals(chunk.content(), outcome.response().evidence().get(0).content());
    assertEquals("chunk-12", outcome.response().citations().get(0).chunkId());
    assertTrue(outcome.state().citationIntegrityPassed());
    assertTrue(outcome.state().groundingPassed());
  }

  @Test
  void skipsQueryRewriteWithoutConversationHistory() {
    when(retriever.retrieve(anyString(), anyInt(), anyDouble()))
        .thenReturn(new RetrievalResult(List.of()));

    pipeline.execute("怎么安装？", List.of(), route);
    verify(gateway, never()).rewrite(anyString(), anyList());

    var history = List.of(new ConversationTurn("user", "上一轮的问题"));
    pipeline.execute("那这个呢？", history, route);
    verify(gateway).rewrite("那这个呢？", history);
  }

  @Test
  void fabricatedCitationRetriesOnceThenFailsClosed() {
    when(retriever.retrieve(anyString(), anyInt(), anyDouble()))
        .thenReturn(new RetrievalResult(List.of(chunk)));
    when(gateway.validateRetrieval(anyString(), anyList()))
        .thenReturn(List.of(new ChunkRelevance("chunk-12", true, 0.9, "相关")));
    when(gateway.generateRagAnswer(anyString(), anyList()))
        .thenReturn(new GeneratedAnswer("伪造回答", List.of("chunk-999")));

    var outcome = pipeline.execute("怎么安装？", List.of(), route);

    assertEquals(ValidationStatus.FAILED, outcome.response().validationStatus());
    assertTrue(outcome.response().citations().isEmpty());
    assertFalse(outcome.state().citationIntegrityPassed());
    verify(gateway, times(2)).generateRagAnswer(anyString(), anyList());
    verify(gateway, never()).validateGrounding(any(), anyList());
  }

  @Test
  void noValidatedChunksNeverCallsAnswerGenerator() {
    when(retriever.retrieve(anyString(), anyInt(), anyDouble()))
        .thenReturn(new RetrievalResult(List.of(chunk)));
    when(gateway.validateRetrieval(anyString(), anyList()))
        .thenReturn(List.of(new ChunkRelevance("chunk-12", false, 0.1, "不相关")));

    var outcome = pipeline.execute("另一个问题", List.of(), route);

    assertEquals(ValidationStatus.NO_EVIDENCE, outcome.response().validationStatus());
    assertEquals(1, outcome.response().retrievedCount());
    assertEquals(0, outcome.response().validatedCount());
    verify(gateway, never()).generateRagAnswer(anyString(), anyList());
  }

  @Test
  void unsupportedGroundingRetriesOnceThenFailsClosed() {
    when(retriever.retrieve(anyString(), anyInt(), anyDouble()))
        .thenReturn(new RetrievalResult(List.of(chunk)));
    when(gateway.validateRetrieval(anyString(), anyList()))
        .thenReturn(List.of(new ChunkRelevance("chunk-12", true, 0.9, "相关")));
    when(gateway.generateRagAnswer(anyString(), anyList()))
        .thenReturn(new GeneratedAnswer("Node.js 必须是 22。", List.of("chunk-12")));
    when(gateway.validateGrounding(any(), anyList()))
        .thenReturn(new GroundingDecision(false, "证据未提到 Node.js 版本", List.of("Node.js 必须是 22")));

    var outcome = pipeline.execute("怎么安装？", List.of(), route);

    assertEquals(ValidationStatus.FAILED, outcome.response().validationStatus());
    assertFalse(outcome.state().groundingPassed());
    verify(gateway, times(2)).validateGrounding(any(), anyList());
  }
}
