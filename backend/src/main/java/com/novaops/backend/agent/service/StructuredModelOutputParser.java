package com.novaops.backend.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.model.ChunkRelevance;
import com.novaops.backend.agent.model.GeneratedAnswer;
import com.novaops.backend.agent.model.GroundingDecision;
import com.novaops.backend.agent.model.QueryRoute;
import com.novaops.backend.agent.model.RouteDecision;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StructuredModelOutputParser {
  private final ObjectMapper objectMapper;

  public StructuredModelOutputParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public RouteDecision parseRoute(String raw) {
    JsonNode root = parseObject(raw);
    String routeValue = requiredText(root, "route").toUpperCase();
    QueryRoute route;
    try {
      route = QueryRoute.valueOf(routeValue);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("unsupported route: " + routeValue, ex);
    }
    return new RouteDecision(route, root.path("reason").asText(""));
  }

  public List<ChunkRelevance> parseRelevance(String raw) {
    JsonNode items = parseObject(raw).path("items");
    if (!items.isArray()) {
      throw new IllegalArgumentException("items must be an array");
    }
    List<ChunkRelevance> result = new ArrayList<>();
    for (JsonNode item : items) {
      result.add(new ChunkRelevance(
          requiredText(item, "chunkId"),
          item.path("relevant").asBoolean(false),
          item.path("score").asDouble(0),
          item.path("reason").asText("")));
    }
    return List.copyOf(result);
  }

  public GeneratedAnswer parseAnswer(String raw) {
    JsonNode root = parseObject(raw);
    JsonNode citations = root.path("citationChunkIds");
    if (!citations.isArray()) {
      throw new IllegalArgumentException("citationChunkIds must be an array");
    }
    List<String> ids = new ArrayList<>();
    citations.forEach(node -> {
      if (node.isTextual()) {
        ids.add(node.asText());
      }
    });
    return new GeneratedAnswer(requiredText(root, "answer"), ids);
  }

  public GroundingDecision parseGrounding(String raw) {
    JsonNode root = parseObject(raw);
    JsonNode unsupported = root.path("unsupportedClaims");
    if (!unsupported.isArray()) {
      throw new IllegalArgumentException("unsupportedClaims must be an array");
    }
    List<String> claims = new ArrayList<>();
    unsupported.forEach(node -> {
      if (node.isTextual()) {
        claims.add(node.asText());
      }
    });
    return new GroundingDecision(
        root.path("supported").asBoolean(false),
        root.path("reason").asText(""),
        claims);
  }

  private JsonNode parseObject(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("model output is empty");
    }
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start < 0 || end <= start) {
      throw new IllegalArgumentException("model output does not contain a JSON object");
    }
    try {
      JsonNode root = objectMapper.readTree(raw.substring(start, end + 1));
      if (!root.isObject()) {
        throw new IllegalArgumentException("model output must be a JSON object");
      }
      return root;
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid model JSON output", ex);
    }
  }

  private String requiredText(JsonNode node, String field) {
    String value = node.path(field).asText("").trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
