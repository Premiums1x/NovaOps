package com.novaops.backend.ticket.dto;

import jakarta.validation.constraints.NotBlank;

public class TicketActionRequest {

  @NotBlank(message = "action 不能为空")
  private String action;

  private String assignee;
  private String targetUser;
  private String remark;

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getAssignee() {
    return assignee;
  }

  public void setAssignee(String assignee) {
    this.assignee = assignee;
  }

  public String getTargetUser() {
    return targetUser;
  }

  public void setTargetUser(String targetUser) {
    this.targetUser = targetUser;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }
}
