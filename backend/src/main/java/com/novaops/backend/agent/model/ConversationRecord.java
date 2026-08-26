package com.novaops.backend.agent.model;
import java.time.LocalDateTime;
public class ConversationRecord {
  private String id,userId,title; private LocalDateTime createdAt,updatedAt;
  public String getId(){return id;} public void setId(String v){id=v;} public String getUserId(){return userId;} public void setUserId(String v){userId=v;} public String getTitle(){return title;} public void setTitle(String v){title=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
