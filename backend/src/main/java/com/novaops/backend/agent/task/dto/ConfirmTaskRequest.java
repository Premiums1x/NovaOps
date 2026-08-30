package com.novaops.backend.agent.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfirmTaskRequest(
    @NotBlank(message = "确认令牌不能为空") String confirmationId,
    @NotNull(message = "缺少确认结果") Boolean approved) {
}
