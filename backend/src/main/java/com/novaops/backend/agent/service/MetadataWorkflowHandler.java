package com.novaops.backend.agent.service;

import com.novaops.backend.agent.model.QueryRoute;
import com.novaops.backend.agent.model.RouteDecision;
import com.novaops.backend.agent.model.ValidationStatus;
import com.novaops.backend.agent.model.WorkflowResult;
import com.novaops.backend.kb.dto.KnowledgeBaseMetadataSnapshot;
import com.novaops.backend.kb.dto.KnowledgeBaseMetadataDocument;
import com.novaops.backend.kb.service.KbMetadataService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MetadataWorkflowHandler {
  private final KbMetadataService metadataService;

  public MetadataWorkflowHandler(KbMetadataService metadataService) {
    this.metadataService = metadataService;
  }

  public WorkflowResult execute(String question, RouteDecision route) {
    try {
      KnowledgeBaseMetadataSnapshot snapshot = metadataService.snapshot();
      if (snapshot.totalDocuments() == 0) {
        return result(route, "当前知识库为空，尚未收录任何文档。", ValidationStatus.NO_EVIDENCE, "knowledge_base_empty");
      }
      String answer = render(snapshot, route);
      return result(route, answer, ValidationStatus.NOT_APPLICABLE, "answered_deterministically_from_metadata");
    } catch (Exception ex) {
      return result(route, "知识库元数据服务暂不可用，请稍后重试。", ValidationStatus.SERVICE_UNAVAILABLE,
          "metadata_service_unavailable");
    }
  }

  private String render(KnowledgeBaseMetadataSnapshot snapshot, RouteDecision route) {
    return switch (route.metadataOperation()) {
      case "list_documents" -> documentList(snapshot, route);
      case "document_detail" -> documentDetail(snapshot, route.documentFilter());
      case "status_summary" -> statusSummary(snapshot);
      case "file_type_summary" -> fileTypeSummary(snapshot);
      default -> overview(snapshot);
    };
  }

  private String overview(KnowledgeBaseMetadataSnapshot snapshot) {
    StringBuilder answer = new StringBuilder("当前知识库共有 ")
        .append(snapshot.totalDocuments()).append(" 个文档，其中 ")
        .append(snapshot.readyDocuments()).append(" 个已就绪。");
    appendDocuments(answer, snapshot.documents().stream().limit(5).toList());
    if (snapshot.truncated()) {
      answer.append("\n文档列表已截断，仅基于已加载的元数据展示。");
    }
    return answer.toString();
  }

  private String documentList(KnowledgeBaseMetadataSnapshot snapshot, RouteDecision route) {
    List<KnowledgeBaseMetadataDocument> documents = snapshot.documents().stream()
        .filter(document -> contains(document.title(), route.documentFilter())
            || contains(document.fileName(), route.documentFilter()))
        .filter(document -> equalsIgnoreCase(document.fileType(), route.fileTypeFilter()))
        .filter(document -> equalsIgnoreCase(document.status(), route.statusFilter()))
        .limit(10)
        .toList();
    if (documents.isEmpty()) {
      return "没有找到符合当前条件的知识库文档。";
    }
    StringBuilder answer = new StringBuilder("符合条件的知识库文档：");
    appendDocuments(answer, documents);
    return answer.toString();
  }

  private String documentDetail(KnowledgeBaseMetadataSnapshot snapshot, String documentFilter) {
    if (documentFilter == null || documentFilter.isBlank()) {
      return "请提供需要查询的知识库文档名称。";
    }
    List<KnowledgeBaseMetadataDocument> matches = snapshot.documents().stream()
        .filter(document -> contains(document.title(), documentFilter) || contains(document.fileName(), documentFilter))
        .limit(5)
        .toList();
    if (matches.isEmpty()) {
      return "没有找到名称包含“" + documentFilter + "”的知识库文档。";
    }
    if (matches.size() > 1) {
      StringBuilder answer = new StringBuilder("找到多个可能的文档，请明确要查询哪一个：");
      appendDocuments(answer, matches);
      return answer.toString();
    }
    KnowledgeBaseMetadataDocument document = matches.get(0);
    return "文档：" + document.title()
        + "\n文件名：" + document.fileName()
        + "\n文件类型：" + upper(document.fileType())
        + "\n处理状态：" + statusText(document.status())
        + "\n内容分块：" + document.chunkCount()
        + "\n更新时间：" + document.updatedAt();
  }

  private String statusSummary(KnowledgeBaseMetadataSnapshot snapshot) {
    return "知识库文档状态：共 " + snapshot.totalDocuments() + " 个，已就绪 "
        + snapshot.readyDocuments() + " 个，其他状态 "
        + Math.max(0, snapshot.totalDocuments() - snapshot.readyDocuments()) + " 个。";
  }

  private String fileTypeSummary(KnowledgeBaseMetadataSnapshot snapshot) {
    Map<String, Long> counts = snapshot.documents().stream().collect(Collectors.groupingBy(
        document -> upper(document.fileType()), LinkedHashMap::new, Collectors.counting()));
    String prefix = snapshot.truncated() ? "已加载文档的文件类型分布（列表已截断）：" : "知识库文件类型分布：";
    return prefix + counts.entrySet().stream()
        .map(entry -> entry.getKey() + " " + entry.getValue() + " 个")
        .collect(Collectors.joining("，")) + "。";
  }

  private void appendDocuments(StringBuilder answer, List<KnowledgeBaseMetadataDocument> documents) {
    for (int index = 0; index < documents.size(); index++) {
      KnowledgeBaseMetadataDocument document = documents.get(index);
      answer.append("\n").append(index + 1).append(". ").append(document.title())
          .append("（").append(upper(document.fileType())).append("，")
          .append(statusText(document.status())).append("，")
          .append(document.chunkCount()).append(" 个分块）");
    }
  }

  private boolean contains(String value, String filter) {
    return filter == null || filter.isBlank()
        || value != null && value.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
  }

  private boolean equalsIgnoreCase(String value, String filter) {
    return filter == null || filter.isBlank() || value != null && value.equalsIgnoreCase(filter);
  }

  private String statusText(String status) {
    if (status == null) {
      return "未知";
    }
    return switch (status) {
      case "READY" -> "已就绪";
      case "PARSING" -> "解析中";
      case "VECTORIZING" -> "向量化中";
      case "FAILED" -> "处理失败";
      default -> status;
    };
  }

  private String upper(String value) {
    return value == null || value.isBlank() ? "UNKNOWN" : value.toUpperCase(Locale.ROOT);
  }

  private WorkflowResult result(RouteDecision route, String answer, ValidationStatus status, String reason) {
    return new WorkflowResult(QueryRoute.METADATA, route.reason(), answer, List.of(), List.of(), false, 0, 0, status, reason);
  }
}
