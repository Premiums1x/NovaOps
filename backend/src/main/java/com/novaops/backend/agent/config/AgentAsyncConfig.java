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

  /**
   * 工具执行线程池：与任务主循环池隔离，避免任务线程阻塞等工具时双重占坑压低并发上限。
   * 工具调用是短任务，队列默认 0（SynchronousQueue）——满载时快速拒绝并由引擎降级为单步失败，
   * 而不是排队后集体超时。
   */
  @Bean(name = "agentToolExecutor")
  public ThreadPoolTaskExecutor agentToolExecutor(
      @Value("${app.agent.tool-executor-core-size:8}") int coreSize,
      @Value("${app.agent.tool-executor-max-size:16}") int maxSize,
      @Value("${app.agent.tool-executor-queue-capacity:0}") int queueCapacity) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(Math.max(1, coreSize));
    executor.setMaxPoolSize(Math.max(coreSize, maxSize));
    executor.setQueueCapacity(Math.max(0, queueCapacity));
    executor.setThreadNamePrefix("nova-agent-tool-");
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
