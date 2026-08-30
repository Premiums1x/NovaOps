package com.novaops.backend.agent.task.model;

public class AgentAuditRecord {

  private String id;
  private String taskId;
  private String userId;
  private String source;
  private String toolName;
  private String argsDigest;
  private String resultDigest;
  private Boolean writeOperation;
  private Boolean confirmed;
  private Boolean allowed;
  private String detail;
  private String createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getToolName() {
    return toolName;
  }

  public void setToolName(String toolName) {
    this.toolName = toolName;
  }

  public String getArgsDigest() {
    return argsDigest;
  }

  public void setArgsDigest(String argsDigest) {
    this.argsDigest = argsDigest;
  }

  public String getResultDigest() {
    return resultDigest;
  }

  public void setResultDigest(String resultDigest) {
    this.resultDigest = resultDigest;
  }

  public Boolean getWriteOperation() {
    return writeOperation;
  }

  public void setWriteOperation(Boolean writeOperation) {
    this.writeOperation = writeOperation;
  }

  public Boolean getConfirmed() {
    return confirmed;
  }

  public void setConfirmed(Boolean confirmed) {
    this.confirmed = confirmed;
  }

  public Boolean getAllowed() {
    return allowed;
  }

  public void setAllowed(Boolean allowed) {
    this.allowed = allowed;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }
}
