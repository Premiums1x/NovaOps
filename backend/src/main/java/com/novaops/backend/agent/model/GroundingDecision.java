package com.novaops.backend.agent.model;

import java.util.List;

public record GroundingDecision(boolean supported, String reason, List<String> unsupportedClaims) {
  public GroundingDecision {
    reason = reason == null ? "" : reason.trim();
    unsupportedClaims = unsupportedClaims == null
        ? List.of()
        : unsupportedClaims.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
  }
}
