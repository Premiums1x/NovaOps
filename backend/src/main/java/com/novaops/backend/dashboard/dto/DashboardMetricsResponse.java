package com.novaops.backend.dashboard.dto;

import java.util.List;

public record DashboardMetricsResponse(
    Range range,
    Overview overview,
    Trend trend,
    List<CategoryItem> categories,
    List<DurationItem> durations) {

  public record Range(String startDate, String endDate) {}

  public record Overview(long ticketTotal, double doneRate, double avgHandleHours, double slaRate) {}

  public record Trend(List<String> dates, List<Integer> created, List<Integer> closed) {}

  public record CategoryItem(String name, long value) {}

  public record DurationItem(String name, double hours) {}
}
