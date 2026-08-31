package com.novaops.backend.kb.service;
import com.novaops.backend.kb.dto.RetrievalResult; import org.springframework.stereotype.Service;
@Service
public class QdrantKbRetrievalService implements KbRetrievalService {
  private final QdrantVectorGateway vectorStore; public QdrantKbRetrievalService(QdrantVectorGateway vectorStore){this.vectorStore=vectorStore;}
  public RetrievalResult retrieve(String query,int topK,double minScore){
    return new RetrievalResult(vectorStore.search(query,topK,minScore));
  }
  @Override
  public RetrievalResult retrieve(String query,int topK,double minScore,String documentFilter){
    return new RetrievalResult(vectorStore.search(query,topK,minScore,documentFilter));
  }
}
