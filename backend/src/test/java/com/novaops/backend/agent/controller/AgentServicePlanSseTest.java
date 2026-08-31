package com.novaops.backend.agent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.mapper.AgentMapper;
import com.novaops.backend.agent.model.AgentMessageRecord;
import com.novaops.backend.agent.model.ConversationRecord;
import com.novaops.backend.agent.model.QueryRoute;
import com.novaops.backend.agent.model.RouteDecision;
import com.novaops.backend.agent.model.ValidationStatus;
import com.novaops.backend.agent.model.WorkflowResult;
import com.novaops.backend.agent.service.AgentService;
import com.novaops.backend.agent.service.AgentWorkflowOrchestrator;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.security.RequestContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentServicePlanSseTest {

  @AfterEach
  void clearSession() {
    RequestContext.clear();
  }

  @Test
  void emitsPlanAndExecutionStepsForAnEmptyRetrievalResult() throws Exception {
    AgentMapper mapper = mock(AgentMapper.class);
    ConversationRecord conversation = new ConversationRecord();
    conversation.setId("conv-1");
    conversation.setUserId("user-1");
    doAnswer(invocation -> {
      ConversationRecord inserted = invocation.getArgument(0);
      conversation.setTitle(inserted.getTitle());
      return null;
    }).when(mapper).insertConversation(any());
    when(mapper.findConversation(any(), any())).thenReturn(conversation);
    when(mapper.listMessages(any())).thenReturn(List.<AgentMessageRecord>of());

    AgentWorkflowOrchestrator orchestrator = mock(AgentWorkflowOrchestrator.class);
    when(orchestrator.route(anyString(), anyList()))
        .thenReturn(new RouteDecision(QueryRoute.RAG, "kb_question"));
    when(orchestrator.execute(anyString(), anyList(), any()))
        .thenReturn(new WorkflowResult(
            QueryRoute.RAG,
            "kb_question",
            "知识库中暂无相关内容，我无法基于可靠资料回答这个问题。",
            List.of(),
            List.of(),
            true,
            0,
            0,
            ValidationStatus.NO_EVIDENCE,
            "no_retrieved_chunks"));

    AgentService service = new AgentService(
        mapper,
        orchestrator,
        new ObjectMapper(),
        Runnable::run,
        120000);

    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentController(service)).build();
    RequestContext.set(new CurrentSession("user-1", "admin", "Admin"));

    MvcResult pending = mockMvc.perform(post("/api/agent/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content("{\"content\":\"如何使用当前知识库？\"}"))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(pending))
        .andExpect(content().string(containsString("event:route")))
        .andExpect(content().string(containsString("event:plan")))
        .andExpect(content().string(containsString("\"action\":\"search_kb\"")))
        .andExpect(content().string(containsString("event:step")))
        .andExpect(content().string(containsString("\"status\":\"running\"")))
        .andExpect(content().string(containsString("\"status\":\"done\"")))
        .andExpect(content().string(containsString("event:done")));
    verify(orchestrator).execute(anyString(), anyList(), any());
    ArgumentCaptor<AgentMessageRecord> savedMessages = ArgumentCaptor.forClass(AgentMessageRecord.class);
    verify(mapper, times(2)).insertMessage(savedMessages.capture());
    assertThat(savedMessages.getAllValues().get(1).getExecutionJson())
        .contains("\"retrievalExecuted\":true")
        .contains("\"retrievedCount\":0")
        .contains("\"answerModelCalled\":false");
  }

  @Test
  void skipsThePlanModelForNonRagRoutes() throws Exception {
    AgentMapper mapper = mock(AgentMapper.class);
    ConversationRecord conversation = new ConversationRecord();
    conversation.setId("conv-1");
    conversation.setUserId("user-1");
    when(mapper.findConversation(any(), any())).thenReturn(conversation);
    when(mapper.listMessages(any())).thenReturn(List.<AgentMessageRecord>of());

    AgentWorkflowOrchestrator orchestrator = mock(AgentWorkflowOrchestrator.class);
    when(orchestrator.route(anyString(), anyList()))
        .thenReturn(new RouteDecision(QueryRoute.CHAT, "greeting"));
    when(orchestrator.execute(anyString(), anyList(), any()))
        .thenReturn(new WorkflowResult(
            QueryRoute.CHAT,
            "greeting",
            "你好，我是 Nova AI。",
            List.of(),
            List.of(),
            false,
            0,
            0,
            ValidationStatus.NOT_APPLICABLE,
            "direct_chat_without_retrieval"));

    AgentService service = new AgentService(
        mapper,
        orchestrator,
        new ObjectMapper(),
        Runnable::run,
        120000);

    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentController(service)).build();
    RequestContext.set(new CurrentSession("user-1", "admin", "Admin"));

    MvcResult pending = mockMvc.perform(post("/api/agent/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content("{\"content\":\"你好\"}"))
        .andExpect(request().asyncStarted())
        .andReturn();

    MvcResult completed = mockMvc.perform(asyncDispatch(pending))
        .andExpect(content().string(containsString("event:plan")))
        .andExpect(content().string(containsString("\"action\":\"answer\"")))
        .andReturn();
    assertThat(completed.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .doesNotContain("\"action\":\"search_kb\"")
        .doesNotContain("\"action\":\"validate\"");
  }
}
