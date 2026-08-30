package com.novaops.backend.agent.service;

import com.novaops.backend.agent.model.ConversationTurn;
import com.novaops.backend.agent.model.RouteDecision;
import com.novaops.backend.agent.model.WorkflowResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AgentWorkflowOrchestrator {
  private final QuestionRouter router;
  private final MetadataWorkflowHandler metadataHandler;
  private final RagPipeline ragPipeline;
  private final ChatWorkflowHandler chatHandler;

  public AgentWorkflowOrchestrator(
      QuestionRouter router,
      MetadataWorkflowHandler metadataHandler,
      RagPipeline ragPipeline,
      ChatWorkflowHandler chatHandler) {
    this.router = router;
    this.metadataHandler = metadataHandler;
    this.ragPipeline = ragPipeline;
    this.chatHandler = chatHandler;
  }

  public WorkflowResult execute(String question, List<ConversationTurn> history) {
    return execute(question, history, route(question, history));
  }

  public RouteDecision route(String question, List<ConversationTurn> history) {
    return router.route(question, history);
  }

  public WorkflowResult execute(String question, List<ConversationTurn> history, RouteDecision route) {
    return switch (route.route()) {
      case METADATA -> metadataHandler.execute(question, route);
      case RAG -> ragPipeline.execute(question, history, route).response();
      case CHAT -> chatHandler.execute(question, history, route);
    };
  }
}
