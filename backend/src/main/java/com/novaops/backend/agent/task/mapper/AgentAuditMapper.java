package com.novaops.backend.agent.task.mapper;

import com.novaops.backend.agent.task.model.AgentAuditRecord;
import org.apache.ibatis.annotations.Param;

public interface AgentAuditMapper {

  void insertAudit(AgentAuditRecord record);

  java.util.List<AgentAuditRecord> listAudits(
      @Param("userId") String userId,
      @Param("limit") int limit);
}
