package com.novaops.backend.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.dto.ChatRequest;
import com.novaops.backend.agent.mapper.AgentMapper;
import com.novaops.backend.agent.model.AgentMessageRecord;
import com.novaops.backend.agent.model.ConversationRecord;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.kb.dto.RetrievalResult;
import com.novaops.backend.kb.service.KbRetrievalService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

class AgentServiceTest {
  private AgentMapper mapper;
  private KbRetrievalService retrievalService;
  private AgentService service;
  private final CurrentSession session = new CurrentSession("user-1", "user", "User", "tenant-a");

  @BeforeEach
  void setUp() {
    mapper = mock(AgentMapper.class);
    retrievalService = mock(KbRetrievalService.class);
    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    when(builder.build()).thenReturn(mock(ChatClient.class));
    service = new AgentService(mapper, retrievalService, builder, mock(CitationValidator.class), new ObjectMapper(), 120000, 5, 0.55);
    ConversationRecord conversation = new ConversationRecord();
    conversation.setId("conv-1");
    conversation.setTenantId("tenant-a");
    conversation.setUserId("user-1");
    when(mapper.findConversation(eq("tenant-a"), eq("user-1"), any())).thenReturn(conversation);
  }

  @Test
  void greetingSkipsRetrievalAndPersistsUnvalidatedAssistantMessage() {
    service.chat(session, request("你好"));

    verify(retrievalService, never()).retrieve(any(), any(), anyInt(), anyDouble());
    ArgumentCaptor<AgentMessageRecord> messages = ArgumentCaptor.forClass(AgentMessageRecord.class);
    verify(mapper, org.mockito.Mockito.times(2)).insertMessage(messages.capture());
    assertEquals("user", messages.getAllValues().get(0).getRole());
    assertEquals("assistant", messages.getAllValues().get(1).getRole());
    assertEquals(null, messages.getAllValues().get(1).getValidationPassed());
  }

  @Test
  void businessQuestionStillUsesTenantScopedRagAndRefusesWithoutContext() {
    when(retrievalService.retrieve("tenant-a", "你好，请告诉我服务器密码策略", 5, 0.55))
        .thenReturn(new RetrievalResult(List.of()));

    service.chat(session, request("你好，请告诉我服务器密码策略"));

    verify(retrievalService).retrieve("tenant-a", "你好，请告诉我服务器密码策略", 5, 0.55);
    ArgumentCaptor<AgentMessageRecord> messages = ArgumentCaptor.forClass(AgentMessageRecord.class);
    verify(mapper, org.mockito.Mockito.times(2)).insertMessage(messages.capture());
    assertEquals("知识库中暂无相关内容，我无法基于可靠资料回答这个问题。", messages.getAllValues().get(1).getContent());
    assertEquals(Boolean.TRUE, messages.getAllValues().get(1).getValidationPassed());
  }

  private ChatRequest request(String content) {
    ChatRequest request = new ChatRequest();
    request.setContent(content);
    return request;
  }
}
