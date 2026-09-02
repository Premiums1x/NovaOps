package com.novaops.backend.agent.task;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 任务会话不跨重启保留，重启后 DB 里 RUNNING/AWAITING_CONFIRM 的记录会成为孤儿；
 * 本任务周期性把它们置为 FAILED（有活跃内存会话的任务不会被误杀）。
 */
@Component
public class AgentTaskCleanupTask {

  private static final Logger log = LoggerFactory.getLogger(AgentTaskCleanupTask.class);

  private final AgentTaskService taskService;
  private final long staleMinutes;

  public AgentTaskCleanupTask(
      AgentTaskService taskService,
      @Value("${app.agent.task.stale-minutes:30}") long staleMinutes) {
    this.taskService = taskService;
    this.staleMinutes = staleMinutes;
  }

  @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
  public void sweep() {
    try {
      int swept = taskService.sweepStaleTasks(Duration.ofMinutes(staleMinutes));
      if (swept > 0) {
        log.info("agent task cleanup: swept {} stale task(s)", swept);
      }
    } catch (Exception ex) {
      log.warn("agent task cleanup failed", ex);
    }
  }
}
