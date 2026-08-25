package com.novaops.backend.auth.dto;

public class CreatedInvitationResponse extends InvitationResponse {
  private String token;

  public String getToken() { return token; }
  public void setToken(String token) { this.token = token; }
}
