package com.novaops.backend.agent.model;
import java.time.LocalDateTime;
public class AgentMessageRecord {
  private String id,conversationId,role,content,citationsJson; private Boolean validationPassed; private LocalDateTime createdAt;
  public String getId(){return id;} public void setId(String v){id=v;} public String getConversationId(){return conversationId;} public void setConversationId(String v){conversationId=v;} public String getRole(){return role;} public void setRole(String v){role=v;} public String getContent(){return content;} public void setContent(String v){content=v;} public String getCitationsJson(){return citationsJson;} public void setCitationsJson(String v){citationsJson=v;} public Boolean getValidationPassed(){return validationPassed;} public void setValidationPassed(Boolean v){validationPassed=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
