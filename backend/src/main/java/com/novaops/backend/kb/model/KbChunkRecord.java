package com.novaops.backend.kb.model;
public class KbChunkRecord {
  private String id,documentId,tenantId,content,vectorId; private Integer chunkIndex;
  public String getId(){return id;} public void setId(String v){id=v;} public String getDocumentId(){return documentId;} public void setDocumentId(String v){documentId=v;}
  public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;} public Integer getChunkIndex(){return chunkIndex;} public void setChunkIndex(Integer v){chunkIndex=v;}
  public String getContent(){return content;} public void setContent(String v){content=v;} public String getVectorId(){return vectorId;} public void setVectorId(String v){vectorId=v;}
}
