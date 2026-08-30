package com.novaops.backend.agent.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novaops.backend.agent.model.QueryRoute;
import com.novaops.backend.agent.model.RouteDecision;
import com.novaops.backend.agent.model.ValidationStatus;
import com.novaops.backend.agent.model.WorkflowResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentWorkflowOrchestratorTest {
  private QuestionRouter router;
  private MetadataWorkflowHandler metadata;
  private RagPipeline rag;
  private ChatWorkflowHandler chat;
  private AgentWorkflowOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    router = mock(QuestionRouter.class);
    metadata = mock(MetadataWorkflowHandler.class);
    rag = mock(RagPipeline.class);
    chat = mock(ChatWorkflowHandler.class);
    orchestrator = new AgentWorkflowOrchestrator(router, metadata, rag, chat);
  }

  @Test
  void metadataRouteCannotFallThroughToRetrieverPipeline() {
    RouteDecision decision = new RouteDecision(QueryRoute.METADATA, "总览");
    WorkflowResult expected = result(QueryRoute.METADATA);
    when(router.route(anyString(), anyList())).thenReturn(decision);
    when(metadata.execute(anyString(), any())).thenReturn(expected);

    assertSame(expected, orchestrator.execute("知识库有什么？", List.of()));
    verify(metadata).execute(anyString(), any());
    verify(rag, never()).execute(anyString(), anyList(), any());
    verify(chat, never()).execute(anyString(), anyList(), any());
  }

  @Test
  void chatRouteCannotInvokeKnowledgeBaseHandlers() {
    RouteDecision decision = new RouteDecision(QueryRoute.CHAT, "闲聊");
    WorkflowResult expected = result(QueryRoute.CHAT);
    when(router.route(anyString(), anyList())).thenReturn(decision);
    when(chat.execute(anyString(), anyList(), any())).thenReturn(expected);

    assertSame(expected, orchestrator.execute("你好", List.of()));
    verify(chat).execute(anyString(), anyList(), any());
    verify(metadata, never()).execute(anyString(), any());
    verify(rag, never()).execute(anyString(), anyList(), any());
  }

  private WorkflowResult result(QueryRoute route) {
    return new WorkflowResult(route, "reason", "answer", List.of(), List.of(), false, 0, 0,
        ValidationStatus.NOT_APPLICABLE, "test");
  }
}
