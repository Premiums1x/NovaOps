package com.novaops.backend.agent.model;

public record RagPipelineOutcome(WorkflowResult response, RagExecutionState state) {}
