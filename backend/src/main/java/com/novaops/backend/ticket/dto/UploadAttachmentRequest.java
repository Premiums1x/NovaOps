package com.novaops.backend.ticket.dto;

import jakarta.validation.constraints.NotBlank;

public class UploadAttachmentRequest {

  @NotBlank(message = "附件名称不能为空")
  private String filename;

  private Long size;

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public Long getSize() {
    return size;
  }

  public void setSize(Long size) {
    this.size = size;
  }
}
