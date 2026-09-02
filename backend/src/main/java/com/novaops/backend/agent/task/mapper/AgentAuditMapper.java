package com.novaops.backend.agent.task.mapper;

import com.novaops.backend.agent.task.model.AgentAuditRecord;
import org.apache.ibatis.annotations.Param;

public interface AgentAuditMapper {

  void insertAudit(AgentAuditRecord record);

  /** 当前暂无调用方（预留读取接口）：任务中心审计明细页（优化计划 T7）的数据来源。 */
  java.util.List<AgentAuditRecord> listAudits(
      @Param("userId") String userId,
      @Param("limit") int limit);

  /** 任务中心的审计明细：按任务返回全部审计记录（时间升序）。 */
  java.util.List<AgentAuditRecord> listAuditsByTask(@Param("taskId") String taskId);

  /** 写操作与已确认次数聚合（任务统计卡片）。 */
  java.util.Map<String, Object> writeAuditStats(@Param("userId") String userId);
}
