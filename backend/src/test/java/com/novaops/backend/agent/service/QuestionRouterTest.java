package com.novaops.backend.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.novaops.backend.agent.model.QueryRoute;
import com.novaops.backend.agent.model.RouteDecision;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuestionRouterTest {
  private AgentModelGateway gateway;
  private QuestionRouter router;

  @BeforeEach
  void setUp() {
    gateway = mock(AgentModelGateway.class);
    router = new QuestionRouter(gateway);
  }

  @Test
  void usesControlledModelDecision() {
    when(gateway.route(anyString(), anyList())).thenReturn(new RouteDecision(QueryRoute.METADATA, "总览问题"));
    assertEquals(QueryRoute.METADATA, router.route("知识库里有什么？", List.of()).route());
  }

  @Test
  void fallsBackToMetadataForOverviewWhenRouterFails() {
    when(gateway.route(anyString(), anyList())).thenThrow(new IllegalArgumentException("bad json"));
    assertEquals(QueryRoute.METADATA, router.route("当前知识库有哪些文档？", List.of()).route());
  }

  @Test
  void fallsBackToChatOnlyForUnambiguousSmallTalk() {
    when(gateway.route(anyString(), anyList())).thenThrow(new IllegalStateException("offline"));
    assertEquals(QueryRoute.CHAT, router.route("你好！", List.of()).route());
    assertEquals(QueryRoute.RAG, router.route("Element Plus X 怎么安装？", List.of()).route());
  }
}
