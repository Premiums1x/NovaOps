package com.novaops.backend.ticket.controller;

import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.security.RequestContext;
import com.novaops.backend.ticket.dto.CreateCommentRequest;
import com.novaops.backend.ticket.dto.CreateTicketRequest;
import com.novaops.backend.ticket.dto.TicketActionRequest;
import com.novaops.backend.ticket.dto.TicketAttachmentResponse;
import com.novaops.backend.ticket.dto.TicketCommentResponse;
import com.novaops.backend.ticket.dto.TicketDetailResponse;
import com.novaops.backend.ticket.dto.TicketListItemResponse;
import com.novaops.backend.ticket.dto.TicketListQuery;
import com.novaops.backend.ticket.dto.UpdateTicketRequest;
import com.novaops.backend.ticket.dto.UploadAttachmentRequest;
import com.novaops.backend.ticket.service.TicketService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

  private final TicketService ticketService;

  public TicketController(TicketService ticketService) {
    this.ticketService = ticketService;
  }

  @GetMapping
  public ApiResponse<PageResult<TicketListItemResponse>> list(@ModelAttribute TicketListQuery query) {
    return ApiResponse.success(ticketService.list(RequestContext.getRequired(), query));
  }

  @GetMapping("/{id}")
  public ApiResponse<TicketDetailResponse> detail(@PathVariable("id") String id) {
    return ApiResponse.success(ticketService.detail(RequestContext.getRequired(), id));
  }

  @PostMapping
  public ApiResponse<TicketDetailResponse> create(@Valid @RequestBody CreateTicketRequest request) {
    return ApiResponse.success(ticketService.create(RequestContext.getRequired(), request), "工单创建成功");
  }

  @PutMapping("/{id}")
  public ApiResponse<TicketDetailResponse> update(@PathVariable("id") String id, @RequestBody UpdateTicketRequest request) {
    return ApiResponse.success(ticketService.update(RequestContext.getRequired(), id, request), "工单更新成功");
  }

  @PostMapping("/{id}/actions")
  public ApiResponse<TicketDetailResponse> action(@PathVariable("id") String id, @Valid @RequestBody TicketActionRequest request) {
    return ApiResponse.success(ticketService.action(RequestContext.getRequired(), id, request), "工单流转成功");
  }

  @GetMapping("/{id}/comments")
  public ApiResponse<List<TicketCommentResponse>> comments(@PathVariable("id") String id) {
    return ApiResponse.success(ticketService.comments(RequestContext.getRequired(), id));
  }

  @PostMapping("/{id}/comments")
  public ApiResponse<TicketCommentResponse> createComment(@PathVariable("id") String id, @Valid @RequestBody CreateCommentRequest request) {
    return ApiResponse.success(ticketService.createComment(RequestContext.getRequired(), id, request), "评论创建成功");
  }

  @PostMapping("/{id}/attachments")
  public ApiResponse<TicketAttachmentResponse> uploadAttachment(
      @PathVariable("id") String id,
      @Valid @RequestBody UploadAttachmentRequest request
  ) {
    return ApiResponse.success(ticketService.uploadAttachment(RequestContext.getRequired(), id, request), "附件上传成功");
  }
}
