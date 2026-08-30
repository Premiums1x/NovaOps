package com.novaops.backend.agent.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.engine.model.PlannedStep;
import com.novaops.backend.agent.engine.model.TaskPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 模型计划输出的容错解析：容忍 markdown 围栏与前后杂文（沿用项目
 * StructuredModelOutputParser 的截取模式）。无法解析时抛出异常，
 * 由引擎决定重试或终止。
 */
@Component
public class TaskPlanParser {

  private final ObjectMapper objectMapper;

  public TaskPlanParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public TaskPlan parse(String raw) {
    int start = raw == null ? -1 : raw.indexOf('{');
    int end = raw == null ? -1 : raw.lastIndexOf('}');
    if (start < 0 || end <= start) {
      throw new IllegalArgumentException("计划输出中找不到 JSON 对象");
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(raw.substring(start, end + 1));
    } catch (Exception ex) {
      throw new IllegalArgumentException("计划输出不是合法 JSON", ex);
    }
    if (root.path("abort").asBoolean(false)) {
      return new TaskPlan(List.of(), true, root.path("note").asText("模型判断目标无法达成"));
    }
    JsonNode stepsNode = root.path("steps");
    if (!stepsNode.isArray() || stepsNode.isEmpty()) {
      throw new IllegalArgumentException("计划输出缺少非空 steps 数组");
    }
    List<PlannedStep> steps = new ArrayList<>();
    int seq = 0;
    for (JsonNode node : stepsNode) {
      String tool = node.path("tool").asText("");
      if (tool.isBlank()) {
        throw new IllegalArgumentException("计划步骤缺少 tool 字段");
      }
      Map<String, Object> args = objectMapper.convertValue(node.path("args"), Map.class);
      steps.add(new PlannedStep(
          ++seq,
          tool.trim(),
          args == null ? Map.of() : args,
          node.path("why").asText("")));
    }
    return new TaskPlan(List.copyOf(steps), false, root.path("note").asText(null));
  }
}
