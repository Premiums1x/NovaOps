package com.novaops.backend.agent.config;

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
}
