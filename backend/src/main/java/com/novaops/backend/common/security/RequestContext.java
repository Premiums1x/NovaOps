package com.novaops.backend.common.security;

public final class RequestContext {

  private static final ThreadLocal<CurrentSession> CURRENT_SESSION = new ThreadLocal<>();

  private RequestContext() {
  }

  public static void set(CurrentSession session) {
    CURRENT_SESSION.set(session);
  }

  public static CurrentSession getRequired() {
    CurrentSession session = CURRENT_SESSION.get();
    if (session == null) {
      throw new IllegalStateException("Current session is missing");
    }
    return session;
  }

  public static void clear() {
    CURRENT_SESSION.remove();
  }
}
