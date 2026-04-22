package com.novaops.backend.common.api;

import java.util.List;

public class PageResult<T> {

  private List<T> list;
  private long page;
  private long pageSize;
  private long total;

  public PageResult() {
  }

  public PageResult(List<T> list, long page, long pageSize, long total) {
    this.list = list;
    this.page = page;
    this.pageSize = pageSize;
    this.total = total;
  }

  public List<T> getList() {
    return list;
  }

  public void setList(List<T> list) {
    this.list = list;
  }

  public long getPage() {
    return page;
  }

  public void setPage(long page) {
    this.page = page;
  }

  public long getPageSize() {
    return pageSize;
  }

  public void setPageSize(long pageSize) {
    this.pageSize = pageSize;
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(long total) {
    this.total = total;
  }
}
