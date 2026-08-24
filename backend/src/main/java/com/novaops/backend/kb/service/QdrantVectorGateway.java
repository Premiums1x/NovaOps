package com.novaops.backend.kb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.kb.config.KbProperties;
import com.novaops.backend.kb.dto.RetrievalChunk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class QdrantVectorGateway {

  public record VectorPoint(String id, String content, Map<String, Object> payload) {
  }

  // embedding 与写入都分批：大文档会切出数千 chunk，
  // 一次性提交必然触发远端接口限制或超时，失败后还得整体重试
  private static final int EMBED_BATCH_SIZE = 16;
  private static final int UPSERT_BATCH_SIZE = 64;

  private final KbProperties properties;
  private final EmbeddingModel embeddingModel;
  private final RestClient client;
  private final ObjectMapper objectMapper;
  private final AtomicBoolean collectionReady = new AtomicBoolean();

  public QdrantVectorGateway(KbProperties properties, EmbeddingModel embeddingModel, RestClient.Builder builder, ObjectMapper objectMapper) {
    this.properties = properties;
    this.embeddingModel = embeddingModel;
    this.objectMapper = objectMapper;
    RestClient.Builder configured = builder.baseUrl(properties.getQdrantBaseUrl());
    if (StringUtils.hasText(properties.getQdrantApiKey())) {
      configured.defaultHeader("api-key", properties.getQdrantApiKey());
    }
    this.client = configured.build();
  }

  public void add(List<VectorPoint> points) {
    if (points.isEmpty()) {
      return;
    }
    List<float[]> vectors = new ArrayList<>(points.size());
    for (int start = 0; start < points.size(); start += EMBED_BATCH_SIZE) {
      List<String> batchTexts = points.subList(start, Math.min(points.size(), start + EMBED_BATCH_SIZE))
          .stream().map(VectorPoint::content).toList();
      vectors.addAll(embeddingModel.embed(batchTexts));
    }
    ensureCollection(vectors.get(0).length);

    for (int start = 0; start < points.size(); start += UPSERT_BATCH_SIZE) {
      List<Map<String, Object>> bodyPoints = new ArrayList<>();
      int end = Math.min(points.size(), start + UPSERT_BATCH_SIZE);
      for (int i = start; i < end; i++) {
        VectorPoint point = points.get(i);
        Map<String, Object> payload = new HashMap<>(point.payload());
        payload.put("content", point.content());
        bodyPoints.add(Map.of("id", point.id(), "vector", vectors.get(i), "payload", payload));
      }
      client.put()
          .uri("/collections/{collection}/points?wait=true", properties.getQdrantCollection())
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("points", bodyPoints))
          .retrieve()
          .toBodilessEntity();
    }
  }

  public void delete(List<String> ids) {
    if (ids.isEmpty()) {
      return;
    }
    client.post()
        .uri("/collections/{collection}/points/delete?wait=true", properties.getQdrantCollection())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("points", ids))
        .retrieve()
        .toBodilessEntity();
  }

  public boolean healthy() {
    try {
      client.get().uri("/readyz").retrieve().toBodilessEntity();
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  public List<RetrievalChunk> search(String tenantId, String query, int topK, double minScore) {
    float[] vector = embeddingModel.embed(query);
    ensureCollection(vector.length);
    Map<String, Object> filter = Map.of("must", List.of(Map.of("key", "tenantId", "match", Map.of("value", tenantId))));
    JsonNode root = client.post()
        .uri("/collections/{collection}/points/search", properties.getQdrantCollection())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("vector", vector, "limit", topK, "score_threshold", minScore, "with_payload", true, "filter", filter))
        .retrieve()
        .body(JsonNode.class);
    List<RetrievalChunk> result = new ArrayList<>();
    if (root == null) {
      return result;
    }
    for (JsonNode hit : root.path("result")) {
      JsonNode payload = hit.path("payload");
      result.add(new RetrievalChunk(payload.path("chunkId").asText(), payload.path("documentId").asText(), payload.path("documentName").asText(), payload.path("content").asText(), hit.path("score").asDouble()));
    }
    return result;
  }

  private void ensureCollection(int dimensions) {
    if (collectionReady.get()) {
      return;
    }
    try {
      client.get().uri("/collections/{collection}", properties.getQdrantCollection()).retrieve().toBodilessEntity();
    } catch (Exception missing) {
      client.put()
          .uri("/collections/{collection}", properties.getQdrantCollection())
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("vectors", Map.of("size", dimensions, "distance", "Cosine")))
          .retrieve()
          .toBodilessEntity();
    }
    collectionReady.set(true);
  }
}
