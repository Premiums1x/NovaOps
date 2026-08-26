package com.novaops.backend.common.security;

public class CurrentSession {

  private final String userId;
  private final String username;
  private final String displayName;

  public CurrentSession(String userId, String username, String displayName) {
    this.userId = userId;
    this.username = username;
    this.displayName = displayName;
  }

  public String getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public String getDisplayName() {
    return displayName;
  }
}
