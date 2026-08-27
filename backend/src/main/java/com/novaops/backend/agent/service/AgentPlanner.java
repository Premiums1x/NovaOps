package com.novaops.backend.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AgentPlanner {

  private static final Logger log = LoggerFactory.getLogger(AgentPlanner.class);
  private final AgentPlanClient client;
  private final AgentPlanParser parser;
  private final boolean enabled;

  public AgentPlanner(
      AgentPlanClient client,
      AgentPlanParser parser,
      @Value("${app.agent.plan-enabled:true}") boolean enabled) {
    this.client = client;
    this.parser = parser;
    this.enabled = enabled;
  }

  public AgentPlanDto plan(String question) {
    if (!enabled) {
      return parser.defaultPlan(question);
    }
    try {
      return parser.parseOrDefault(client.generate(question), question);
    } catch (Exception exception) {
      log.warn("agent plan generation failed, using default plan: {}", exception.getMessage());
      return parser.defaultPlan(question);
    }
  }
}
