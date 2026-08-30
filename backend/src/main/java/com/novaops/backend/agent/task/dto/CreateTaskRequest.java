package com.novaops.backend.agent.task.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTaskRequest(@NotBlank(message = "目标不能为空") String goal) {
}
