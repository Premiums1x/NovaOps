package com.novaops.backend.agent.task.mapper;

import com.novaops.backend.agent.task.model.AgentAuditRecord;
import org.apache.ibatis.annotations.Param;

public interface AgentAuditMapper {

  void insertAudit(AgentAuditRecord record);

  /** 当前暂无调用方（预留读取接口）：任务中心审计明细页（优化计划 T7）的数据来源。 */
  java.util.List<AgentAuditRecord> listAudits(
      @Param("userId") String userId,
      @Param("limit") int limit);
}
