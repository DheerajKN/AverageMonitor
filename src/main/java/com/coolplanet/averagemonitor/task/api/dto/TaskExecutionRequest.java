package com.coolplanet.averagemonitor.task.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TaskExecutionRequest(
        @NotNull(message = "durationMillis is required")
        @PositiveOrZero(message = "durationMillis must be greater than or equal to zero")
        Long durationMillis
) {
}