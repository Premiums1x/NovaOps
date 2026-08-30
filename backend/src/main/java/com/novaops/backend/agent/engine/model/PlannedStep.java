package com.novaops.backend.agent.engine.model;

import java.util.Map;

/**
 * 模型产出的一条计划步骤。seq 为其在当次计划中的位置（从 1 开始）。
 */
public record PlannedStep(int seq, String tool, Map<String, Object> args, String why) {
}
