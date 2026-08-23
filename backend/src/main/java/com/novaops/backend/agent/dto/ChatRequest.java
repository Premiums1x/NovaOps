package com.novaops.backend.agent.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;
public class ChatRequest { private String conversationId; @NotBlank @Size(max=4000) private String content; public String getConversationId(){return conversationId;} public void setConversationId(String v){conversationId=v;} public String getContent(){return content;} public void setContent(String v){content=v;} }
