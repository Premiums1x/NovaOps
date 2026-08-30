package com.novaops.backend.agent.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具入参 JSON Schema（object 类型子集）的构建器。
 * 产物同时服务于：模型的计划提示词、MCP tools/list 的 inputSchema。
 * 用代码而非手写 JSON 字符串，保证 Schema 可编译、可校验。
 */
public final class ToolSchema {

  private final Map<String, Object> properties = new LinkedHashMap<>();
  private final List<String> required = new ArrayList<>();

  private ToolSchema() {
  }

  /** 包装一个已符合 JSON Schema object 结构的 Map（用于远端 MCP 工具的 inputSchema）。 */
  public static ToolSchema fromMap(Map<String, Object> schema) {
    ToolSchema wrapper = new ToolSchema();
    wrapper.built = schema == null ? Map.of("type", "object", "properties", Map.of(), "required", List.of()) : schema;
    return wrapper;
  }

  private Map<String, Object> built;

  public static ToolSchema object() {
    return new ToolSchema();
  }

  public ToolSchema string(String name, String description, boolean isRequired) {
    properties.put(name, Map.of("type", "string", "description", description));
    if (isRequired) {
      required.add(name);
    }
    return this;
  }

  public ToolSchema enumString(String name, String description, List<String> options, boolean isRequired) {
    properties.put(name, Map.of("type", "string", "description", description, "enum", options));
    if (isRequired) {
      required.add(name);
    }
    return this;
  }

  public ToolSchema integer(String name, String description, boolean isRequired) {
    properties.put(name, Map.of("type", "integer", "description", description));
    if (isRequired) {
      required.add(name);
    }
    return this;
  }

  public ToolSchema boolean_(String name, String description, boolean isRequired) {
    properties.put(name, Map.of("type", "boolean", "description", description));
    if (isRequired) {
      required.add(name);
    }
    return this;
  }

  public Map<String, Object> build() {
    if (built != null) {
      return built;
    }
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("type", "object");
    root.put("properties", Map.copyOf(properties));
    root.put("required", List.copyOf(required));
    return root;
  }
}
