package com.novaops.backend.agent.task.model;

public class AgentTaskStepRecord {

  private String id;
  private String taskId;
  private Integer seq;
  private String kind;
  private String toolName;
  private String argsJson;
  private String observationJson;
  private String status;
  private Integer revision;
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

  public Integer getSeq() {
    return seq;
  }

  public void setSeq(Integer seq) {
    this.seq = seq;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public String getToolName() {
    return toolName;
  }

  public void setToolName(String toolName) {
    this.toolName = toolName;
  }

  public String getArgsJson() {
    return argsJson;
  }

  public void setArgsJson(String argsJson) {
    this.argsJson = argsJson;
  }

  public String getObservationJson() {
    return observationJson;
  }

  public void setObservationJson(String observationJson) {
    this.observationJson = observationJson;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getRevision() {
    return revision;
  }

  public void setRevision(Integer revision) {
    this.revision = revision;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }
}
