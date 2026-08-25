package com.novaops.backend.dashboard.controller;

import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.security.RequestContext;
import com.novaops.backend.dashboard.dto.DashboardMetricsResponse;
import com.novaops.backend.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @GetMapping("/metrics")
  public ApiResponse<DashboardMetricsResponse> metrics(
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate) {
    return ApiResponse.success(dashboardService.metrics(RequestContext.getRequired(), startDate, endDate));
  }
}
