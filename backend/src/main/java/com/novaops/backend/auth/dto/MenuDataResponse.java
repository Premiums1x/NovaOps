package com.novaops.backend.auth.dto;

import java.util.List;

public class MenuDataResponse {

  private List<MenuItemResponse> menus;
  private List<String> permissions;

  public List<MenuItemResponse> getMenus() {
    return menus;
  }

  public void setMenus(List<MenuItemResponse> menus) {
    this.menus = menus;
  }

  public List<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(List<String> permissions) {
    this.permissions = permissions;
  }
}
