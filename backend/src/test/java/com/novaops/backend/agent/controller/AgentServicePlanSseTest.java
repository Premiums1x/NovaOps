package com.novaops.backend.agent.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.mapper.AgentMapper;
import com.novaops.backend.agent.model.ConversationRecord;
import com.novaops.backend.agent.service.AgentPlanParser;
import com.novaops.backend.agent.service.AgentPlanner;
import com.novaops.backend.agent.service.AgentService;
import com.novaops.backend.agent.service.CitationValidator;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.security.RequestContext;
import com.novaops.backend.kb.dto.RetrievalResult;
import com.novaops.backend.kb.service.KbRetrievalService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
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
    when(mapper.findConversation("user-1", "conv-1")).thenReturn(conversation);
    when(mapper.findConversation(any(), any())).thenReturn(conversation);
    KbRetrievalService retrieval = mock(KbRetrievalService.class);
    when(retrieval.retrieve(any(), any(Integer.class), any(Double.class)))
        .thenReturn(new RetrievalResult(List.of()));
    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    when(builder.build()).thenReturn(mock(ChatClient.class));
    AgentPlanner planner = new AgentPlanner(
        question -> """
            {"steps":[
              {"action":"search_kb","query":"NovaOps 知识库使用说明","reason":"改写检索词"},
              {"action":"answer","reason":"组织回答"},
              {"action":"validate","reason":"校验引用"}
            ]}
            """,
        new AgentPlanParser(new ObjectMapper()),
        true);
    AgentService service = new AgentService(
        mapper,
        retrieval,
        builder,
        mock(CitationValidator.class),
        planner,
        new ObjectMapper(),
        120000,
        5,
        0.55);
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentController(service)).build();
    RequestContext.set(new CurrentSession("user-1", "admin", "Admin"));

    MvcResult pending = mockMvc.perform(post("/api/agent/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content("{\"content\":\"如何使用当前知识库？\"}"))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(pending))
        .andExpect(content().string(containsString("event:plan")))
        .andExpect(content().string(containsString("\"action\":\"search_kb\"")))
        .andExpect(content().string(containsString("event:step")))
        .andExpect(content().string(containsString("\"status\":\"running\"")))
        .andExpect(content().string(containsString("\"status\":\"done\"")));
    verify(retrieval).retrieve("NovaOps 知识库使用说明", 5, 0.55);
  }
}
