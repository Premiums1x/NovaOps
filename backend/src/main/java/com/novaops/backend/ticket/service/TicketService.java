package com.novaops.backend.ticket.service;

import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.util.DateTimeUtils;
import com.novaops.backend.common.util.IdGenerator;
import com.novaops.backend.ticket.dto.CreateCommentRequest;
import com.novaops.backend.ticket.dto.CreateTicketRequest;
import com.novaops.backend.ticket.dto.TicketActionRequest;
import com.novaops.backend.ticket.dto.TicketAttachmentResponse;
import com.novaops.backend.ticket.dto.TicketCommentResponse;
import com.novaops.backend.ticket.dto.TicketDetailResponse;
import com.novaops.backend.ticket.dto.TicketListItemResponse;
import com.novaops.backend.ticket.dto.TicketListQuery;
import com.novaops.backend.ticket.dto.TicketTimelineItemResponse;
import com.novaops.backend.ticket.dto.UpdateTicketRequest;
import com.novaops.backend.ticket.dto.UploadAttachmentRequest;
import com.novaops.backend.ticket.mapper.TicketMapper;
import com.novaops.backend.ticket.model.TicketAssetRelationRecord;
import com.novaops.backend.ticket.model.TicketAttachmentRecord;
import com.novaops.backend.ticket.model.TicketCommentRecord;
import com.novaops.backend.ticket.model.TicketRecord;
import com.novaops.backend.ticket.model.TicketTimelineRecord;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TicketService {

  private final TicketMapper ticketMapper;
  private final AuthService authService;

  public TicketService(TicketMapper ticketMapper, AuthService authService) {
    this.ticketMapper = ticketMapper;
    this.authService = authService;
  }

  public PageResult<TicketListItemResponse> list(CurrentSession session, TicketListQuery query) {
    long page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
    long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
    long offset = (page - 1) * pageSize;

    LocalDateTime startDate = DateTimeUtils.parseIsoDateTime(query.getStartDate());
    LocalDateTime endDate = DateTimeUtils.parseIsoDateTime(query.getEndDate());

    long total = ticketMapper.countTickets(
        query.getStatus(),
        query.getPriority(),
        query.getKeyword(),
        startDate,
        endDate
    );

    List<TicketRecord> records = ticketMapper.queryTickets(
        query.getStatus(),
        query.getPriority(),
        query.getKeyword(),
        startDate,
        endDate,
        offset,
        pageSize
    );

    Map<String, List<String>> assetMap = buildAssetMap(records.stream().map(TicketRecord::getId).toList());
    List<TicketListItemResponse> list = records.stream()
        .map(record -> toListItem(record, assetMap.getOrDefault(record.getId(), Collections.emptyList())))
        .toList();

    return new PageResult<>(list, page, pageSize, total);
  }

  public TicketDetailResponse detail(CurrentSession session, String ticketId) {
    TicketRecord record = requireTicket(ticketId);
    return buildDetail(record);
  }

  @Transactional
  public TicketDetailResponse create(CurrentSession session, CreateTicketRequest request) {
    TicketRecord record = new TicketRecord();
    LocalDateTime now = LocalDateTime.now();

    record.setId(IdGenerator.ticketId());
    record.setTitle(request.getTitle());
    record.setDescription(request.getDescription());
    record.setStatus("pending");
    record.setPriority(StringUtils.hasText(request.getPriority()) ? request.getPriority() : "medium");
    record.setAssignee(StringUtils.hasText(request.getAssignee()) ? request.getAssignee() : "Unassigned");
    record.setCreator(session.getUsername());
    record.setDueDate(DateTimeUtils.parseIsoDateTime(request.getDueDate()));
    record.setCreatedAt(now);
    record.setUpdatedAt(now);

    ticketMapper.insertTicket(record);
    replaceAssetRelations(record.getId(), request.getAssetIds());
    ticketMapper.insertTimeline(buildTimeline(record.getId(), "create", session.getUsername(), "新建工单", null, "pending", now));

    return buildDetail(record);
  }

  @Transactional
  public TicketDetailResponse update(CurrentSession session, String ticketId, UpdateTicketRequest request) {
    TicketRecord record = requireTicket(ticketId);

    if (StringUtils.hasText(request.getTitle())) {
      record.setTitle(request.getTitle());
    }
    if (StringUtils.hasText(request.getDescription())) {
      record.setDescription(request.getDescription());
    }
    if (StringUtils.hasText(request.getPriority())) {
      record.setPriority(request.getPriority());
    }
    if (StringUtils.hasText(request.getAssignee())) {
      record.setAssignee(request.getAssignee());
    }
    if (StringUtils.hasText(request.getDueDate())) {
      record.setDueDate(DateTimeUtils.parseIsoDateTime(request.getDueDate()));
    }

    record.setUpdatedAt(LocalDateTime.now());
    ticketMapper.updateTicket(record);
    if (request.getAssetIds() != null) {
      replaceAssetRelations(record.getId(), request.getAssetIds());
    }
    ticketMapper.insertTimeline(buildTimeline(record.getId(), "update", session.getUsername(), "更新工单信息", null, null, record.getUpdatedAt()));

    return buildDetail(record);
  }

  @Transactional
  public TicketDetailResponse action(CurrentSession session, String ticketId, TicketActionRequest request) {
    // 流转动作按动作细分权限，接口注解无法表达这种动态映射
    String action = request.getAction();
    String requiredPermission = switch (action) {
      case "assign" -> "ticket:assign";
      case "transfer" -> "ticket:transfer";
      case "close" -> "ticket:close";
      case "advance" -> "ticket:advance";
      case "reject" -> "ticket:reject";
      case "approve" -> "ticket:approve";
      default -> throw new BusinessException(400, "不支持的工单动作");
    };
    authService.requirePermission(session, requiredPermission);

    TicketRecord record = requireTicket(ticketId);
    String previousStatus = record.getStatus();

    // 状态机前置校验：非法转移直接拒绝（409），防止跨状态倒退/重复关单/越权流转
    validateTransition(action, previousStatus);

    switch (action) {
      case "assign" -> {
        if (StringUtils.hasText(request.getAssignee())) {
          record.setAssignee(request.getAssignee());
        }
        if ("pending".equals(previousStatus)) {
          record.setStatus("processing");
        }
        // 非 pending：仅换人，状态保持不变
      }
      case "transfer" -> {
        if (StringUtils.hasText(request.getTargetUser())) {
          record.setAssignee(request.getTargetUser());
        }
        if ("review".equals(previousStatus)) {
          record.setStatus("processing");
        }
        // 非 review：仅换人，状态保持不变
      }
      case "advance" -> record.setStatus("review");    // processing → review（提交复核）
      case "approve" -> record.setStatus("done");      // review → done（复核通过）
      case "reject" -> record.setStatus("processing"); // review → processing（驳回）
      case "close" -> record.setStatus("done");        // processing/review → done（关闭）
      default -> throw new BusinessException(400, "不支持的工单动作");
    }

    record.setUpdatedAt(LocalDateTime.now());
    ticketMapper.updateTicket(record);
    ticketMapper.insertTimeline(buildTimeline(
        record.getId(),
        action,
        session.getUsername(),
        request.getRemark(),
        previousStatus,
        record.getStatus(),
        record.getUpdatedAt()
    ));

    return buildDetail(record);
  }

  /**
   * 工单状态机转移矩阵：只有命中合法转移才放行，否则抛 409。
   * 状态：pending(待处理) → processing(处理中) → review(待复核) → done(已完成)。
   * 动作语义：assign(指派)、advance(提交复核)、approve(复核通过)、reject(驳回)、close(关闭)、transfer(转派)。
   */
  private void validateTransition(String action, String currentStatus) {
    switch (action) {
      case "assign", "transfer" -> {
        if ("done".equals(currentStatus)) {
          throw new BusinessException(409, "已完成的工单不可再" + ("assign".equals(action) ? "指派" : "转派"));
        }
      }
      case "advance" -> {
        if (!"processing".equals(currentStatus)) {
          throw new BusinessException(409, "仅处理中的工单可提交复核");
        }
      }
      case "approve" -> {
        if (!"review".equals(currentStatus)) {
          throw new BusinessException(409, "仅待复核的工单可复核通过");
        }
      }
      case "reject" -> {
        if (!"review".equals(currentStatus)) {
          throw new BusinessException(409, "仅待复核的工单可驳回");
        }
      }
      case "close" -> {
        if ("pending".equals(currentStatus)) {
          throw new BusinessException(409, "待处理的工单不可直接关闭，请先指派");
        }
        if ("done".equals(currentStatus)) {
          throw new BusinessException(409, "工单已关闭，不可重复关闭");
        }
      }
      default -> throw new BusinessException(400, "不支持的工单动作");
    }
  }

  public List<TicketCommentResponse> comments(CurrentSession session, String ticketId) {
    requireTicket(ticketId);
    return ticketMapper.listComments(ticketId).stream().map(this::toComment).toList();
  }

  @Transactional
  public TicketCommentResponse createComment(CurrentSession session, String ticketId, CreateCommentRequest request) {
    TicketRecord record = requireTicket(ticketId);
    LocalDateTime now = LocalDateTime.now();

    TicketCommentRecord comment = new TicketCommentRecord();
    comment.setId(IdGenerator.randomId("cm"));
    comment.setTicketId(ticketId);
    comment.setAuthor(session.getUsername());
    comment.setContent(request.getContent().trim());
    comment.setCreatedAt(now);
    ticketMapper.insertComment(comment);

    record.setUpdatedAt(now);
    ticketMapper.updateTicket(record);
    return toComment(comment);
  }

  @Transactional
  public TicketAttachmentResponse uploadAttachment(CurrentSession session, String ticketId, UploadAttachmentRequest request) {
    TicketRecord record = requireTicket(ticketId);
    LocalDateTime now = LocalDateTime.now();

    TicketAttachmentRecord attachment = new TicketAttachmentRecord();
    attachment.setId(IdGenerator.randomId("att"));
    attachment.setTicketId(ticketId);
    attachment.setName(request.getFilename());
    attachment.setSize(request.getSize() == null ? 0L : request.getSize());
    attachment.setUrl("/mock-attachments/" + ticketId + "/" + request.getFilename().replace(" ", "%20"));
    attachment.setCreatedAt(now);
    ticketMapper.insertAttachment(attachment);

    record.setUpdatedAt(now);
    ticketMapper.updateTicket(record);
    return toAttachment(attachment);
  }

  private TicketRecord requireTicket(String ticketId) {
    TicketRecord record = ticketMapper.findTicket(ticketId);
    if (record == null) {
      throw new BusinessException(404, "工单不存在");
    }
    return record;
  }

  private TicketDetailResponse buildDetail(TicketRecord record) {
    TicketDetailResponse response = new TicketDetailResponse();
    response.setId(record.getId());
    response.setTitle(record.getTitle());
    response.setDescription(record.getDescription());
    response.setStatus(record.getStatus());
    response.setPriority(record.getPriority());
    response.setAssignee(record.getAssignee());
    response.setCreator(record.getCreator());
    response.setCreatedAt(DateTimeUtils.toIsoString(record.getCreatedAt()));
    response.setUpdatedAt(DateTimeUtils.toIsoString(record.getUpdatedAt()));
    response.setDueDate(DateTimeUtils.toIsoString(record.getDueDate()));
    response.setAssetIds(ticketMapper.listAssetIds(record.getId()));
    response.setTimeline(ticketMapper.listTimeline(record.getId()).stream().map(this::toTimeline).toList());
    response.setComments(ticketMapper.listComments(record.getId()).stream().map(this::toComment).toList());
    response.setAttachments(ticketMapper.listAttachments(record.getId()).stream().map(this::toAttachment).toList());
    return response;
  }

  private TicketListItemResponse toListItem(TicketRecord record, List<String> assetIds) {
    TicketListItemResponse response = new TicketListItemResponse();
    response.setId(record.getId());
    response.setTitle(record.getTitle());
    response.setStatus(record.getStatus());
    response.setPriority(record.getPriority());
    response.setAssignee(record.getAssignee());
    response.setCreator(record.getCreator());
    response.setCreatedAt(DateTimeUtils.toIsoString(record.getCreatedAt()));
    response.setUpdatedAt(DateTimeUtils.toIsoString(record.getUpdatedAt()));
    response.setAssetIds(assetIds);
    return response;
  }

  private TicketTimelineItemResponse toTimeline(TicketTimelineRecord record) {
    TicketTimelineItemResponse response = new TicketTimelineItemResponse();
    response.setId(record.getId());
    response.setAction(record.getAction());
    response.setOperator(record.getOperator());
    response.setRemark(record.getRemark());
    response.setFromStatus(record.getFromStatus());
    response.setToStatus(record.getToStatus());
    response.setCreatedAt(DateTimeUtils.toIsoString(record.getCreatedAt()));
    return response;
  }

  private TicketCommentResponse toComment(TicketCommentRecord record) {
    TicketCommentResponse response = new TicketCommentResponse();
    response.setId(record.getId());
    response.setAuthor(record.getAuthor());
    response.setContent(record.getContent());
    response.setCreatedAt(DateTimeUtils.toIsoString(record.getCreatedAt()));
    return response;
  }

  private TicketAttachmentResponse toAttachment(TicketAttachmentRecord record) {
    TicketAttachmentResponse response = new TicketAttachmentResponse();
    response.setId(record.getId());
    response.setName(record.getName());
    response.setUrl(record.getUrl());
    response.setSize(record.getSize());
    response.setCreatedAt(DateTimeUtils.toIsoString(record.getCreatedAt()));
    return response;
  }

  private void replaceAssetRelations(String ticketId, List<String> assetIds) {
    ticketMapper.deleteAssetRelations(ticketId);
    if (assetIds == null || assetIds.isEmpty()) {
      return;
    }
    assetIds.stream()
        .filter(StringUtils::hasText)
        .map(String::trim)
        .distinct()
        .forEach(assetId -> ticketMapper.insertAssetRelation(ticketId, assetId));
  }

  private TicketTimelineRecord buildTimeline(
      String ticketId,
      String action,
      String operator,
      String remark,
      String fromStatus,
      String toStatus,
      LocalDateTime createdAt
  ) {
    TicketTimelineRecord record = new TicketTimelineRecord();
    record.setId(IdGenerator.randomId("tl"));
    record.setTicketId(ticketId);
    record.setAction(action);
    record.setOperator(operator);
    record.setRemark(remark);
    record.setFromStatus(fromStatus);
    record.setToStatus(toStatus);
    record.setCreatedAt(createdAt);
    return record;
  }

  private Map<String, List<String>> buildAssetMap(List<String> ticketIds) {
    if (ticketIds.isEmpty()) {
      return Collections.emptyMap();
    }

    List<TicketAssetRelationRecord> relations = ticketMapper.listAssetRelationsByTicketIds(ticketIds);
    Map<String, List<String>> assetMap = new LinkedHashMap<>();
    for (TicketAssetRelationRecord relation : relations) {
      assetMap.computeIfAbsent(relation.getTicketId(), key -> new ArrayList<>()).add(relation.getAssetId());
    }
    return assetMap;
  }
}
