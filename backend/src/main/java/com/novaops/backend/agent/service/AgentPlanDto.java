package com.novaops.backend.agent.service;

import java.util.List;

public record AgentPlanDto(List<AgentPlanStepDto> steps) {
}
