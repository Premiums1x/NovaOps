package com.novaops.backend.agent.controller;

import com.novaops.backend.agent.dto.ChatRequest;
import com.novaops.backend.agent.dto.ConversationDetailResponse;
import com.novaops.backend.agent.model.ConversationRecord;
import com.novaops.backend.agent.service.AgentService;
import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.security.RequestContext;
import com.novaops.backend.common.security.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

  private final AgentService service;

  public AgentController(AgentService service) {
    this.service = service;
  }

  @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RequirePermission("agent:chat")
  public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
    return service.chat(RequestContext.getRequired(), request);
  }

  @GetMapping("/conversations")
  @RequirePermission("agent:chat")
  public ApiResponse<List<ConversationRecord>> conversations() {
    return ApiResponse.success(service.conversations(RequestContext.getRequired()));
  }

  @GetMapping("/conversations/{id}")
  @RequirePermission("agent:chat")
  public ApiResponse<ConversationDetailResponse> detail(@PathVariable String id) {
    return ApiResponse.success(service.detail(RequestContext.getRequired(), id));
  }
}
