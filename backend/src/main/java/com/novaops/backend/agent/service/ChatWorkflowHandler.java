package com.novaops.backend.agent.service;

import com.novaops.backend.agent.model.ConversationTurn;
import com.novaops.backend.agent.model.QueryRoute;
import com.novaops.backend.agent.model.RouteDecision;
import com.novaops.backend.agent.model.ValidationStatus;
import com.novaops.backend.agent.model.WorkflowResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ChatWorkflowHandler {
  private final AgentModelGateway modelGateway;

  public ChatWorkflowHandler(AgentModelGateway modelGateway) {
    this.modelGateway = modelGateway;
  }

  public WorkflowResult execute(String question, List<ConversationTurn> history, RouteDecision route) {
    try {
      String answer = modelGateway.answerChat(question, history);
      return result(route, answer, ValidationStatus.NOT_APPLICABLE, "direct_chat_without_retrieval");
    } catch (Exception ex) {
      return result(route, "模型服务暂不可用，请稍后重试。", ValidationStatus.SERVICE_UNAVAILABLE,
          "chat_model_unavailable");
    }
  }

  private WorkflowResult result(RouteDecision route, String answer, ValidationStatus status, String reason) {
    return new WorkflowResult(QueryRoute.CHAT, route.reason(), answer, List.of(), List.of(), false, 0, 0, status, reason);
  }
}
