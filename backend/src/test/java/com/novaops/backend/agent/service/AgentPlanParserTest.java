package com.novaops.backend.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentPlanParserTest {

  private final AgentPlanParser parser = new AgentPlanParser(new ObjectMapper());

  @Test
  void parsesAValidPublicActionPlan() {
    AgentPlanDto plan = parser.parseOrDefault("""
        {"steps":[
          {"action":"search_kb","query":"知识库使用说明","reason":"定位相关资料"},
          {"action":"answer","reason":"根据资料组织答案"},
          {"action":"validate","reason":"核对引用编号"}
        ]}
        """, "如何使用当前知识库？");

    assertThat(plan.steps()).extracting(AgentPlanStepDto::action)
        .containsExactly("search_kb", "answer", "validate");
    assertThat(plan.steps().get(0).query()).isEqualTo("知识库使用说明");
    assertThat(plan.steps()).allMatch(step -> "pending".equals(step.status()));
  }

  @Test
  void acceptsMarkdownFencesAndSurroundingText() {
    AgentPlanDto plan = parser.parseOrDefault("""
        下面是计划：
        ```json
        {"steps":[{"action":"search_kb","query":"NovaOps 知识库","reason":"检索"},{"action":"answer","reason":"回答"},{"action":"validate","reason":"校验"}]}
        ```
        """, "原问题");

    assertThat(plan.steps()).hasSize(3);
    assertThat(plan.steps().get(0).query()).isEqualTo("NovaOps 知识库");
  }

  @Test
  void fallsBackToTheStableThreeStepPlanForMalformedOutput() {
    AgentPlanDto plan = parser.parseOrDefault("not-json", "原问题");

    assertThat(plan.steps()).extracting(AgentPlanStepDto::action)
        .isEqualTo(List.of("search_kb", "answer", "validate"));
    assertThat(plan.steps().get(0).query()).isEqualTo("原问题");
  }
}
