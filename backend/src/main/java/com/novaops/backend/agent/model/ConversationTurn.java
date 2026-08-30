package com.novaops.backend.agent.model;

public record ConversationTurn(String role, String content) {
  public ConversationTurn {
    role = role == null ? "" : role.trim();
    content = content == null ? "" : content.trim();
  }
}
