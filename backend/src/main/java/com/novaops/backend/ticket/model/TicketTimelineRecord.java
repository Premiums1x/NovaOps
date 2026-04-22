package com.novaops.backend.ticket.model;

import java.time.LocalDateTime;

public class TicketTimelineRecord {

  private String id;
  private String ticketId;
  private String action;
  private String operator;
  private String remark;
  private String fromStatus;
  private String toStatus;
  private LocalDateTime createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTicketId() {
    return ticketId;
  }

  public void setTicketId(String ticketId) {
    this.ticketId = ticketId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getOperator() {
    return operator;
  }

  public void setOperator(String operator) {
    this.operator = operator;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }

  public String getFromStatus() {
    return fromStatus;
  }

  public void setFromStatus(String fromStatus) {
    this.fromStatus = fromStatus;
  }

  public String getToStatus() {
    return toStatus;
  }

  public void setToStatus(String toStatus) {
    this.toStatus = toStatus;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
