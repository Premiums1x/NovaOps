package com.novaops.backend.agent.service;

import com.novaops.backend.agent.model.ConversationTurn;
import com.novaops.backend.agent.model.QueryRoute;
import com.novaops.backend.agent.model.RouteDecision;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class QuestionRouter {
  private static final Pattern METADATA_PATTERN = Pattern.compile(
      "(知识库|资料库|文档库).*(有什么|有哪些|内容|文档|资料|主题|概览|概况|目录|清单|是否有|有没有)"
          + "|(有什么|有哪些|是否有|有没有).*(知识库|资料库|文档库)");
  private static final Pattern CHAT_PATTERN = Pattern.compile(
      "^(你好|您好|嗨|谢谢|感谢|再见|hi|hello|thanks|thank you)[!！。,.，?？\\s]*$",
      Pattern.CASE_INSENSITIVE);

  private final AgentModelGateway modelGateway;

  public QuestionRouter(AgentModelGateway modelGateway) {
    this.modelGateway = modelGateway;
  }

  public RouteDecision route(String question, List<ConversationTurn> history) {
    try {
      RouteDecision decision = modelGateway.route(question, history);
      if (decision == null || decision.route() == null) {
        throw new IllegalArgumentException("empty route decision");
      }
      return decision;
    } catch (Exception ex) {
      return fallback(question);
    }
  }

  private RouteDecision fallback(String question) {
    String normalized = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
    if (METADATA_PATTERN.matcher(normalized).find()) {
      return new RouteDecision(QueryRoute.METADATA, "路由模型不可用，依据知识库总览特征安全降级");
    }
    if (CHAT_PATTERN.matcher(normalized).matches()) {
      return new RouteDecision(QueryRoute.CHAT, "路由模型不可用，依据明确闲聊特征安全降级");
    }
    return new RouteDecision(QueryRoute.RAG, "路由模型不可用，默认进入受知识库约束的安全路径");
  }
}
