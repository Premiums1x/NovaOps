package com.novaops.backend.auth.dto;

import java.util.List;

public class MenuItemResponse {

  private String id;
  private String title;
  private String name;
  private String path;
  private String component;
  private String icon;
  private String permission;
  private Boolean keepAlive;
  private List<MenuItemResponse> children;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getComponent() {
    return component;
  }

  public void setComponent(String component) {
    this.component = component;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public String getPermission() {
    return permission;
  }

  public void setPermission(String permission) {
    this.permission = permission;
  }

  public Boolean getKeepAlive() {
    return keepAlive;
  }

  public void setKeepAlive(Boolean keepAlive) {
    this.keepAlive = keepAlive;
  }

  public List<MenuItemResponse> getChildren() {
    return children;
  }

  public void setChildren(List<MenuItemResponse> children) {
    this.children = children;
  }
}
