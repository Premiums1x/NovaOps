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
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StructuredModelOutputParser {
  private static final Set<String> ROUTE_FIELDS = Set.of(
      "version", "route", "intent", "confidence", "reasonCode", "semanticQuery",
      "metadataOperation", "documentFilter", "fileTypeFilter", "statusFilter", "topK", "reason");
  private static final Set<String> METADATA_OPERATIONS = Set.of(
      "overview", "list_documents", "document_detail", "status_summary", "file_type_summary");
  private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{1,63}");
  private final ObjectMapper objectMapper;

  public StructuredModelOutputParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public RouteDecision parseRoute(String raw) {
    JsonNode root = parseStrictObject(raw);
    root.fieldNames().forEachRemaining(field -> {
      if (!ROUTE_FIELDS.contains(field)) {
        throw new IllegalArgumentException("unsupported route field: " + field);
      }
    });
    if (!"1".equals(requiredText(root, "version"))) {
      throw new IllegalArgumentException("unsupported route schema version");
    }
    String routeValue = requiredText(root, "route").toUpperCase();
    QueryRoute route;
    try {
      route = QueryRoute.valueOf(routeValue);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("unsupported route: " + routeValue, ex);
    }
    double confidence = root.path("confidence").asDouble(-1);
    if (confidence < 0 || confidence > 1) {
      throw new IllegalArgumentException("confidence must be between 0 and 1");
    }
    String intent = requiredCode(root, "intent");
    String reasonCode = requiredCode(root, "reasonCode");
    String semanticQuery = textField(root, "semanticQuery");
    String operation = textField(root, "metadataOperation");
    if (route == QueryRoute.METADATA && !METADATA_OPERATIONS.contains(operation)) {
      throw new IllegalArgumentException("unsupported metadata operation: " + operation);
    }
    if (!root.path("topK").isIntegralNumber()) {
      throw new IllegalArgumentException("topK must be an integer");
    }
    Integer topK = root.path("topK").asInt();
    if (topK < 1 || topK > 20) {
      throw new IllegalArgumentException("topK must be between 1 and 20");
    }
    if (route == QueryRoute.RAG && semanticQuery.isBlank()) {
      throw new IllegalArgumentException("semanticQuery must not be blank for RAG");
    }
    return new RouteDecision(
        route,
        intent,
        confidence,
        reasonCode,
        semanticQuery,
        operation,
        textField(root, "documentFilter"),
        textField(root, "fileTypeFilter"),
        textField(root, "statusFilter"),
        topK,
        requiredText(root, "reason"));
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

  private JsonNode parseStrictObject(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("model output is empty");
    }
    String value = raw.trim();
    if (!value.startsWith("{") || !value.endsWith("}")) {
      throw new IllegalArgumentException("route output must be a single JSON object");
    }
    try {
      JsonNode root = objectMapper.readTree(value);
      if (!root.isObject()) {
        throw new IllegalArgumentException("route output must be a JSON object");
      }
      return root;
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid route JSON output", ex);
    }
  }

  private String textField(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (!value.isTextual()) {
      throw new IllegalArgumentException(field + " must be a string");
    }
    return value.asText().trim();
  }

  private String requiredCode(JsonNode node, String field) {
    String value = requiredText(node, field);
    if (!CODE.matcher(value).matches()) {
      throw new IllegalArgumentException(field + " must be snake_case");
    }
    return value;
  }
}
