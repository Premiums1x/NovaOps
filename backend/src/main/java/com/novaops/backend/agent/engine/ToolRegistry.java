package com.novaops.backend.agent.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 工具注册表：启动时收集所有 {@link AgentToolExecutor} Bean，读取 {@link AgentTool}
 * 注解构建描述。提供按用户权限过滤的工具清单（模型只能"看见"其有权使用的工具），
 * 以及 MCP 侧的只读工具目录。同名工具在启动期即失败，杜绝静默覆盖。
 */
@Component
public class ToolRegistry {

  private final Map<String, ToolHandle> tools = new LinkedHashMap<>();

  public ToolRegistry(List<AgentToolExecutor> executors) {
    for (AgentToolExecutor executor : executors) {
      AgentTool annotation = executor.getClass().getAnnotation(AgentTool.class);
      if (annotation == null) {
        throw new IllegalStateException(
            "AgentToolExecutor 实现缺少 @AgentTool 注解: " + executor.getClass().getName());
      }
      if (tools.containsKey(annotation.name())) {
        throw new IllegalStateException("重复的 Agent 工具名: " + annotation.name());
      }
      tools.put(annotation.name(), new ToolHandle(
          new ToolDescriptor(
              annotation.name(),
              annotation.title(),
              annotation.description(),
              annotation.permission(),
              annotation.category(),
              executor.inputSchema().build()),
          executor));
    }
  }

  public Optional<ToolHandle> find(String name) {
    return Optional.ofNullable(tools.get(name));
  }

  /** 动态注册（MCP 远端工具桥接用）：同名工具已存在时返回 false，不覆盖。 */
  public synchronized boolean register(ToolDescriptor descriptor, AgentToolExecutor executor) {
    if (descriptor == null || tools.containsKey(descriptor.name())) {
      return false;
    }
    tools.put(descriptor.name(), new ToolHandle(descriptor, executor));
    return true;
  }

  /** 注解式动态注册（内部使用）：名字取自类上 @AgentTool 注解。 */
  public synchronized boolean register(AgentToolExecutor executor) {
    AgentTool annotation = executor.getClass().getAnnotation(AgentTool.class);
    if (annotation == null) {
      throw new IllegalStateException(
          "AgentToolExecutor 实现缺少 @AgentTool 注解: " + executor.getClass().getName());
    }
    return register(new ToolDescriptor(
        annotation.name(),
        annotation.title(),
        annotation.description(),
        annotation.permission(),
        annotation.category(),
        executor.inputSchema().build()), executor);
  }

  /** 按用户权限过滤后的工具描述（permission 为空视为登录可用）。 */
  public List<ToolDescriptor> toolsFor(Set<String> permissions) {
    return tools.values().stream()
        .map(ToolHandle::descriptor)
        .filter(descriptor -> permitted(descriptor, permissions))
        .toList();
  }

  /** MCP 侧只暴露 READ 类工具。 */
  public List<ToolDescriptor> readableTools() {
    return tools.values().stream()
        .map(ToolHandle::descriptor)
        .filter(descriptor -> descriptor.category() == AgentToolCategory.READ)
        .toList();
  }

  public static boolean permitted(ToolDescriptor descriptor, Set<String> permissions) {
    String permission = descriptor.permission();
    return permission == null || permission.isBlank() || permissions.contains(permission);
  }
}
