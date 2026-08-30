package com.novaops.backend.agent.service;

import com.novaops.backend.agent.model.QueryRoute;
import com.novaops.backend.agent.model.RouteDecision;
import com.novaops.backend.agent.model.ValidationStatus;
import com.novaops.backend.agent.model.WorkflowResult;
import com.novaops.backend.kb.dto.KnowledgeBaseMetadataSnapshot;
import com.novaops.backend.kb.service.KbMetadataService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MetadataWorkflowHandler {
  private final KbMetadataService metadataService;
  private final AgentModelGateway modelGateway;

  public MetadataWorkflowHandler(KbMetadataService metadataService, AgentModelGateway modelGateway) {
    this.metadataService = metadataService;
    this.modelGateway = modelGateway;
  }

  public WorkflowResult execute(String question, RouteDecision route) {
    try {
      KnowledgeBaseMetadataSnapshot snapshot = metadataService.snapshot();
      if (snapshot.totalDocuments() == 0) {
        return result(route, "当前知识库为空，尚未收录任何文档。", ValidationStatus.NO_EVIDENCE, "knowledge_base_empty");
      }
      String answer = modelGateway.answerMetadata(question, snapshot);
      return result(route, answer, ValidationStatus.NOT_APPLICABLE, "answered_from_document_metadata");
    } catch (Exception ex) {
      return result(route, "知识库元数据服务暂不可用，请稍后重试。", ValidationStatus.SERVICE_UNAVAILABLE,
          "metadata_service_unavailable");
    }
  }

  private WorkflowResult result(RouteDecision route, String answer, ValidationStatus status, String reason) {
    return new WorkflowResult(QueryRoute.METADATA, route.reason(), answer, List.of(), List.of(), false, 0, 0, status, reason);
  }
}
