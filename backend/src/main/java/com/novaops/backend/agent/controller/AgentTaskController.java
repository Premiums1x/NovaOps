package com.novaops.backend.agent.controller;

import com.novaops.backend.agent.task.AgentTaskService;
import com.novaops.backend.agent.task.dto.ConfirmTaskRequest;
import com.novaops.backend.agent.task.dto.CreateTaskRequest;
import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.security.RequestContext;
import com.novaops.backend.common.security.RequirePermission;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 任务型 Agent（智能体工作台）API：创建任务、附着事件流、人工确认、查询与取消。
 */
@RestController
@RequestMapping("/api/agent/tasks")
public class AgentTaskController {

  private final AgentTaskService taskService;

  public AgentTaskController(AgentTaskService taskService) {
    this.taskService = taskService;
  }

  @PostMapping
  @RequirePermission("agent:task")
  public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateTaskRequest request) {
    return ApiResponse.success(taskService.create(RequestContext.getRequired(), request.goal()));
  }

  @PostMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RequirePermission("agent:task")
  public SseEmitter stream(@PathVariable("id") String id) {
    return taskService.openStream(RequestContext.getRequired(), id);
  }

  @PostMapping("/{id}/confirm")
  @RequirePermission("agent:task")
  public ApiResponse<Map<String, Object>> confirm(
      @PathVariable("id") String id, @Valid @RequestBody ConfirmTaskRequest request) {
    return ApiResponse.success(taskService.confirm(
        RequestContext.getRequired(), id, request.confirmationId(), request.approved()));
  }

  @PostMapping("/{id}/cancel")
  @RequirePermission("agent:task")
  public ApiResponse<Void> cancel(@PathVariable("id") String id) {
    taskService.cancel(RequestContext.getRequired(), id);
    return ApiResponse.success(null, "任务已取消");
  }

  @GetMapping("/{id}")
  @RequirePermission("agent:task")
  public ApiResponse<Map<String, Object>> detail(@PathVariable("id") String id) {
    return ApiResponse.success(taskService.detail(RequestContext.getRequired(), id));
  }

  @GetMapping("/{id}/audits")
  @RequirePermission("agent:task")
  public ApiResponse<Object> audits(@PathVariable("id") String id) {
    return ApiResponse.success(taskService.audits(RequestContext.getRequired(), id));
  }

  @GetMapping("/stats")
  @RequirePermission("agent:task")
  public ApiResponse<Map<String, Object>> stats() {
    return ApiResponse.success(taskService.stats(RequestContext.getRequired()));
  }

  @GetMapping
  @RequirePermission("agent:task")
  public ApiResponse<Object> list() {
    return ApiResponse.success(taskService.list(RequestContext.getRequired()));
  }
}
