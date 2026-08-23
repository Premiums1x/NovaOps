package com.novaops.backend.agent.controller;
import com.novaops.backend.agent.dto.ChatRequest; import com.novaops.backend.agent.dto.ConversationDetailResponse; import com.novaops.backend.agent.model.ConversationRecord; import com.novaops.backend.agent.service.AgentService; import com.novaops.backend.common.api.ApiResponse; import com.novaops.backend.common.security.RequestContext; import jakarta.validation.Valid; import java.util.List; import org.springframework.http.MediaType; import org.springframework.web.bind.annotation.*; import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
@RestController @RequestMapping("/api/agent")
public class AgentController {
  private final AgentService service; public AgentController(AgentService service){this.service=service;}
  @PostMapping(value="/chat",produces=MediaType.TEXT_EVENT_STREAM_VALUE) public SseEmitter chat(@Valid @RequestBody ChatRequest request){return service.chat(RequestContext.getRequired(),request);}
  @GetMapping("/conversations") public ApiResponse<List<ConversationRecord>> conversations(){return ApiResponse.success(service.conversations(RequestContext.getRequired()));}
  @GetMapping("/conversations/{id}") public ApiResponse<ConversationDetailResponse> detail(@PathVariable String id){return ApiResponse.success(service.detail(RequestContext.getRequired(),id));}
}
