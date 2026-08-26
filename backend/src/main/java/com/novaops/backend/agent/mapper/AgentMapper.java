package com.novaops.backend.agent.mapper;
import com.novaops.backend.agent.model.AgentMessageRecord; import com.novaops.backend.agent.model.ConversationRecord; import java.util.List; import org.apache.ibatis.annotations.Param;
public interface AgentMapper {
  void insertConversation(ConversationRecord record); ConversationRecord findConversation(@Param("userId") String userId,@Param("id") String id); List<ConversationRecord> listConversations(@Param("userId") String userId); void touchConversation(@Param("id") String id); void insertMessage(AgentMessageRecord record); List<AgentMessageRecord> listMessages(@Param("conversationId") String conversationId);
}
