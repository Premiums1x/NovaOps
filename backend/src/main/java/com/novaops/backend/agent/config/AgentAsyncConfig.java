package com.novaops.backend.agent.config;

import com.novaops.backend.agent.engine.model.EngineConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AgentAsyncConfig {
  @Bean(name = "agentTaskExecutor")
  public ThreadPoolTaskExecutor agentTaskExecutor(
      @Value("${app.agent.executor-core-size:4}") int coreSize,
      @Value("${app.agent.executor-max-size:12}") int maxSize,
      @Value("${app.agent.executor-queue-capacity:100}") int queueCapacity) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(Math.max(1, coreSize));
    executor.setMaxPoolSize(Math.max(coreSize, maxSize));
    executor.setQueueCapacity(Math.max(1, queueCapacity));
    executor.setThreadNamePrefix("nova-agent-");
    executor.setWaitForTasksToCompleteOnShutdown(false);
    executor.initialize();
    return executor;
  }

  @Bean
  public EngineConfig agentEngineConfig(
      @Value("${app.agent.task.max-steps:10}") int maxSteps,
      @Value("${app.agent.task.max-revisions:2}") int maxRevisions,
      @Value("${app.agent.task.observation-max-chars:2000}") int observationMaxChars,
      @Value("${app.agent.task.step-timeout-seconds:20}") int stepTimeoutSeconds) {
    return new EngineConfig(maxSteps, maxRevisions, observationMaxChars, stepTimeoutSeconds, 1);
  }
}
