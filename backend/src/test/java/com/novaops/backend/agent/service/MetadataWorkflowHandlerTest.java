package com.novaops.backend.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novaops.backend.agent.model.QueryRoute;
import com.novaops.backend.agent.model.RouteDecision;
import com.novaops.backend.agent.model.ValidationStatus;
import com.novaops.backend.kb.dto.KnowledgeBaseMetadataSnapshot;
import com.novaops.backend.kb.service.KbMetadataService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MetadataWorkflowHandlerTest {
  @Test
  void emptyKnowledgeBaseReturnsDeterministicAnswerWithoutCallingModel() {
    KbMetadataService metadata = Mockito.mock(KbMetadataService.class);
    AgentModelGateway gateway = Mockito.mock(AgentModelGateway.class);
    when(metadata.snapshot()).thenReturn(new KnowledgeBaseMetadataSnapshot(0, 0, false, List.of()));

    var result = new MetadataWorkflowHandler(metadata, gateway)
        .execute("知识库有什么？", new RouteDecision(QueryRoute.METADATA, "总览"));

    assertEquals(ValidationStatus.NO_EVIDENCE, result.validationStatus());
    assertEquals(false, result.retrievalExecuted());
    verify(gateway, never()).answerMetadata(Mockito.anyString(), Mockito.any());
  }
}
