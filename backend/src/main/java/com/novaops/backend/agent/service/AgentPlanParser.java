package com.novaops.backend.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AgentPlanParser {

  private static final List<String> ACTIONS = List.of("search_kb", "answer", "validate");
  private final ObjectMapper objectMapper;

  public AgentPlanParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public AgentPlanDto parseOrDefault(String raw, String question) {
    try {
      int start = raw == null ? -1 : raw.indexOf('{');
      int end = raw == null ? -1 : raw.lastIndexOf('}');
      if (start < 0 || end <= start) {
        return defaultPlan(question);
      }
      JsonNode stepsNode = objectMapper.readTree(raw.substring(start, end + 1)).path("steps");
      if (!stepsNode.isArray()) {
        return defaultPlan(question);
      }
      List<AgentPlanStepDto> steps = new ArrayList<>();
      for (String action : ACTIONS) {
        JsonNode source = findAction(stepsNode, action);
        if (source == null) {
          return defaultPlan(question);
        }
        steps.add(toStep(source, action, question));
      }
      return new AgentPlanDto(List.copyOf(steps));
    } catch (Exception ignored) {
      return defaultPlan(question);
    }
  }

  public AgentPlanDto defaultPlan(String question) {
    String safeQuestion = safeText(question, "当前问题", 500);
    return new AgentPlanDto(List.of(
        new AgentPlanStepDto("search_kb", "检索知识库", safeQuestion, "定位与问题相关的知识库资料", "pending"),
        new AgentPlanStepDto("answer", "生成回答", null, "依据检索结果组织带引用的回答", "pending"),
        new AgentPlanStepDto("validate", "校验引用", null, "核对引用编号与知识库资料是否一致", "pending")));
  }

  private JsonNode findAction(JsonNode stepsNode, String action) {
    for (JsonNode node : stepsNode) {
      if (action.equals(node.path("action").asText())) {
        return node;
      }
    }
    return null;
  }

  private AgentPlanStepDto toStep(JsonNode node, String action, String question) {
    String query = "search_kb".equals(action)
        ? safeText(node.path("query").asText(), question, 500)
        : null;
    String reason = safeText(node.path("reason").asText(), defaultReason(action), 200);
    return new AgentPlanStepDto(action, label(action), query, reason, "pending");
  }

  private String label(String action) {
    return switch (action) {
      case "search_kb" -> "检索知识库";
      case "answer" -> "生成回答";
      default -> "校验引用";
    };
  }

  private String defaultReason(String action) {
    return switch (action) {
      case "search_kb" -> "定位与问题相关的知识库资料";
      case "answer" -> "依据检索结果组织带引用的回答";
      default -> "核对引用编号与知识库资料是否一致";
    };
  }

  private String safeText(String value, String fallback, int maxLength) {
    String text = value == null || value.isBlank() ? fallback : value.trim();
    if (text == null || text.isBlank()) {
      return "当前问题";
    }
    return text.substring(0, Math.min(maxLength, text.length()));
  }
}
