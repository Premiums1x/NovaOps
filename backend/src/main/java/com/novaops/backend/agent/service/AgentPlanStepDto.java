package com.novaops.backend.agent.service;

public record AgentPlanStepDto(
    String action,
    String label,
    String query,
    String reason,
    String status) {
}
