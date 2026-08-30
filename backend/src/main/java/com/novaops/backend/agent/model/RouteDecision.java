package com.novaops.backend.agent.model;

public record RouteDecision(QueryRoute route, String reason) {
  public RouteDecision {
    if (route == null) {
      throw new IllegalArgumentException("route must not be null");
    }
    reason = reason == null ? "" : reason.trim();
  }
}
