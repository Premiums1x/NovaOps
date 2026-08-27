package com.novaops.backend.agent.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.novaops.backend.agent.service.AgentService;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.security.RequestContext;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;

class AgentControllerSseTest {

  @AfterEach
  void clearSession() {
    RequestContext.clear();
  }

  @Test
  void synchronousServiceFailureIsReturnedAsAnSseErrorEvent() throws Exception {
    AgentService service = mock(AgentService.class);
    when(service.chat(any(), any())).thenThrow(new IllegalStateException("database unavailable"));
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentController(service)).build();
    RequestContext.set(new CurrentSession("user-1", "admin", "Admin"));

    MvcResult pending = mockMvc.perform(post("/api/agent/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content("{\"content\":\"如何使用当前知识库？\"}"))
        .andExpect(request().asyncStarted())
        .andReturn();

    MvcResult completed = mockMvc.perform(asyncDispatch(pending))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andExpect(content().string(containsString("event:error")))
        .andReturn();
    assertThat(completed.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .contains("服务异常，请稍后重试");
  }
}
