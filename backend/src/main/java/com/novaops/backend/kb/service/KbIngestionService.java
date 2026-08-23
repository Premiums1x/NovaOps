package com.novaops.backend.kb.service;

import com.novaops.backend.common.util.IdGenerator;
import com.novaops.backend.kb.mapper.KbMapper;
import com.novaops.backend.kb.model.KbChunkRecord;
import com.novaops.backend.kb.model.KbDocumentRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class KbIngestionService {
  private final KbMapper mapper; private final TextChunker chunker; private final QdrantVectorGateway vectorStore;
  public KbIngestionService(KbMapper mapper,TextChunker chunker,QdrantVectorGateway vectorStore){this.mapper=mapper;this.chunker=chunker;this.vectorStore=vectorStore;}
  @Async
  public void process(KbDocumentRecord source){
    List<String> vectorIds=new ArrayList<>();
    try(var input=Files.newInputStream(Path.of(source.getStoragePath()))){
      String text=parseWithTika(input); List<String> chunks=chunker.split(text);
      if(chunks.isEmpty()) throw new IllegalStateException("文档未解析出有效文本");
      mapper.updateStatus(source.getId(),"VECTORIZING",0,null);
      List<QdrantVectorGateway.VectorPoint> vectors=new ArrayList<>(); List<KbChunkRecord> records=new ArrayList<>();
      for(int index=0;index<chunks.size();index++){
        String chunkId=IdGenerator.randomId("chunk"), vectorId=UUID.randomUUID().toString(); vectorIds.add(vectorId);
        Map<String,Object> metadata=Map.of("tenantId",source.getTenantId(),"documentId",source.getId(),"chunkId",chunkId,"documentName",source.getTitle());
        vectors.add(new QdrantVectorGateway.VectorPoint(vectorId,chunks.get(index),metadata));
        KbChunkRecord record=new KbChunkRecord(); record.setId(chunkId);record.setDocumentId(source.getId());record.setTenantId(source.getTenantId());record.setChunkIndex(index);record.setContent(chunks.get(index));record.setVectorId(vectorId);records.add(record);
      }
      vectorStore.add(vectors);
      records.forEach(mapper::insertChunk);
      mapper.updateStatus(source.getId(),"READY",records.size(),null);
    } catch(Exception ex){
      if(!vectorIds.isEmpty()){try{vectorStore.delete(vectorIds);}catch(Exception ignored){}}
      String message=ex.getMessage()==null?"解析或向量化失败":ex.getMessage();
      mapper.updateStatus(source.getId(),"FAILED",0,message.substring(0,Math.min(900,message.length())));
    }
  }
  private String parseWithTika(java.io.InputStream input) throws Exception {
    Class<?> type=Class.forName("org.apache.tika.Tika"); Object tika=type.getConstructor().newInstance();
    return (String)type.getMethod("parseToString",java.io.InputStream.class).invoke(tika,input);
  }
}
