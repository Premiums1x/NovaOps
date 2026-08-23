package com.novaops.backend.kb.dto;
import jakarta.validation.constraints.Max; import jakarta.validation.constraints.Min;
public class KbDocumentQuery {
  @Min(1) private int page=1; @Min(1) @Max(100) private int pageSize=10; private String keyword,fileType,status;
  public int getPage(){return page;} public void setPage(int v){page=v;} public int getPageSize(){return pageSize;} public void setPageSize(int v){pageSize=v;}
  public String getKeyword(){return keyword;} public void setKeyword(String v){keyword=v;} public String getFileType(){return fileType;} public void setFileType(String v){fileType=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
}
