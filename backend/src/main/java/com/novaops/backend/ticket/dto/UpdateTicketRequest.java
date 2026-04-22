package com.novaops.backend.ticket.dto;

import java.util.List;

public class UpdateTicketRequest {

  private String title;
  private String description;
  private String priority;
  private String assignee;
  private String dueDate;
  private List<String> assetIds;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getPriority() {
    return priority;
  }

  public void setPriority(String priority) {
    this.priority = priority;
  }

  public String getAssignee() {
    return assignee;
  }

  public void setAssignee(String assignee) {
    this.assignee = assignee;
  }

  public String getDueDate() {
    return dueDate;
  }

  public void setDueDate(String dueDate) {
    this.dueDate = dueDate;
  }

  public List<String> getAssetIds() {
    return assetIds;
  }

  public void setAssetIds(List<String> assetIds) {
    this.assetIds = assetIds;
  }
}
