package com.novaops.backend.agent.task.mapper;

import com.novaops.backend.agent.task.model.AgentTaskRecord;
import com.novaops.backend.agent.task.model.AgentTaskStepRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AgentTaskMapper {

  void insertTask(AgentTaskRecord record);

  void updateTaskStatus(@Param("id") String id, @Param("status") String status);

  void finishTask(
      @Param("id") String id,
      @Param("status") String status,
      @Param("resultText") String resultText,
      @Param("errorText") String errorText);

  void updatePlanJson(@Param("id") String id, @Param("planJson") String planJson);

  AgentTaskRecord findTask(@Param("id") String id, @Param("userId") String userId);

  List<AgentTaskRecord> listTasks(@Param("userId") String userId, @Param("limit") int limit);

  void insertStep(AgentTaskStepRecord record);

  List<AgentTaskStepRecord> listSteps(@Param("taskId") String taskId);

  /** 无活跃内存会话判定的 TTL 清扫候选：超过截止时间仍处非终态的任务（单次上限 200 条）。 */
  List<String> findStaleTaskIds(@Param("cutoff") java.time.LocalDateTime cutoff);

  /** 任务统计：按状态分组计数。 */
  List<java.util.Map<String, Object>> countTasksByStatus(@Param("userId") String userId);

  /** 任务统计：人均任务的平均工具步数（单值）。 */
  Double avgToolStepsPerTask(@Param("userId") String userId);
}
