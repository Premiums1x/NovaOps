package com.novaops.backend.agent.engine;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 任务型 Agent 工具声明。工具类实现 {@link AgentToolExecutor} 并标注本注解，
 * 由 {@link ToolRegistry} 在启动时扫描注册。
 *
 * <p>name 全局唯一（点分命名，如 ticket.assign）；permission 为调用该工具所需的
 * 权限码，为空表示登录即可用；WRITE 类工具必须在 confirmed=false 时只做校验与
 * 效果预览、不产生任何副作用，这是引擎依赖的安全契约。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AgentTool {
  String name();

  String title();

  String description();

  String permission() default "";

  AgentToolCategory category() default AgentToolCategory.READ;
}
