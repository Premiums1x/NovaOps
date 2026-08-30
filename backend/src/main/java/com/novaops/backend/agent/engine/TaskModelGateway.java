package com.novaops.backend.agent.engine;

import com.novaops.backend.agent.engine.ToolDescriptor;
import com.novaops.backend.agent.engine.model.PlannedStep;
import com.novaops.backend.agent.engine.model.StepOutcome;
import com.novaops.backend.agent.engine.model.TaskPlan;
import java.util.List;

/**
 * 计划/重规划/总结的模型访问接口。引擎只依赖本接口，
 * Spring AI 实现位于 task 包，测试用脚本化假实现。
 */
public interface TaskModelGateway {

  /** 为目标生成初始计划。 */
  TaskPlan plan(String goal, List<ToolDescriptor> tools);

  /** 基于已发生的观察修订剩余步骤（有界重规划）。返回 abort=true 表示模型判断无法继续。 */
  TaskPlan replan(String goal, List<ToolDescriptor> tools, List<StepOutcome> history);

  /** 基于全部观察生成最终报告。 */
  String summarize(String goal, List<StepOutcome> history);
}
