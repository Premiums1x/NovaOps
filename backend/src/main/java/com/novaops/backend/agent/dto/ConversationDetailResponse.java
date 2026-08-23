package com.novaops.backend.agent.dto;
import com.novaops.backend.agent.model.AgentMessageRecord; import com.novaops.backend.agent.model.ConversationRecord; import java.util.List;
public record ConversationDetailResponse(ConversationRecord conversation,List<AgentMessageRecord> messages) {}
