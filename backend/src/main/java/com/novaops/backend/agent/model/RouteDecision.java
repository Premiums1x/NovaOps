package com.novaops.backend.agent.model;

public record RouteDecision(
    QueryRoute route,
    String intent,
    double confidence,
    String reasonCode,
    String semanticQuery,
    String metadataOperation,
    String documentFilter,
    String fileTypeFilter,
    String statusFilter,
    Integer topK,
    String reason) {
  public RouteDecision {
    if (route == null) {
      throw new IllegalArgumentException("route must not be null");
    }
    intent = normalize(intent);
    confidence = Math.max(0, Math.min(1, confidence));
    reasonCode = normalize(reasonCode);
    semanticQuery = normalize(semanticQuery);
    metadataOperation = normalize(metadataOperation);
    documentFilter = normalize(documentFilter);
    fileTypeFilter = normalize(fileTypeFilter);
    statusFilter = normalize(statusFilter);
    topK = topK == null ? null : Math.max(1, Math.min(20, topK));
    reason = reason == null ? "" : reason.trim();
  }

  public RouteDecision(QueryRoute route, String reason) {
    this(route, "", 1, "", "", "", "", "", "", null, reason);
  }

  public static RouteDecision clarify(String reasonCode, String reason) {
    return new RouteDecision(QueryRoute.CLARIFY, "ambiguous", 0, reasonCode, "", "", "", "", "", null, reason);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
