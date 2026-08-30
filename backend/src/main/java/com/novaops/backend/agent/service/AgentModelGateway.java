package com.novaops.backend.agent.service;

import com.novaops.backend.agent.model.ChunkRelevance;
import com.novaops.backend.agent.model.ConversationTurn;
import com.novaops.backend.agent.model.GeneratedAnswer;
import com.novaops.backend.agent.model.GroundingDecision;
import com.novaops.backend.agent.model.RouteDecision;
import com.novaops.backend.kb.dto.KnowledgeBaseMetadataSnapshot;
import com.novaops.backend.kb.dto.RetrievalChunk;
import java.util.List;

public interface AgentModelGateway {
  RouteDecision route(String question, List<ConversationTurn> history);

  String rewrite(String question, List<ConversationTurn> history);

  List<ChunkRelevance> validateRetrieval(String query, List<RetrievalChunk> chunks);

  GeneratedAnswer generateRagAnswer(String question, List<RetrievalChunk> evidence);

  GroundingDecision validateGrounding(GeneratedAnswer answer, List<RetrievalChunk> evidence);

  String answerMetadata(String question, KnowledgeBaseMetadataSnapshot snapshot);

  String answerChat(String question, List<ConversationTurn> history);
}
