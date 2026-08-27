package com.novaops.backend.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AgentPlannerTest {

  @Test
  void disabledPlannerUsesDefaultPlanWithoutCallingTheModel() {
    AgentPlanClient client = mock(AgentPlanClient.class);
    AgentPlanner planner = new AgentPlanner(client, new AgentPlanParser(new ObjectMapper()), false);

    AgentPlanDto plan = planner.plan("问题");

    verify(client, never()).generate("问题");
    assertThat(plan.steps()).extracting(AgentPlanStepDto::action)
        .containsExactly("search_kb", "answer", "validate");
  }

  @Test
  void modelFailureFallsBackInsteadOfBreakingChat() {
    AgentPlanClient client = mock(AgentPlanClient.class);
    when(client.generate("问题")).thenThrow(new IllegalStateException("model unavailable"));
    AgentPlanner planner = new AgentPlanner(client, new AgentPlanParser(new ObjectMapper()), true);

    AgentPlanDto plan = planner.plan("问题");

    assertThat(plan.steps()).extracting(AgentPlanStepDto::action)
        .containsExactly("search_kb", "answer", "validate");
  }
}
