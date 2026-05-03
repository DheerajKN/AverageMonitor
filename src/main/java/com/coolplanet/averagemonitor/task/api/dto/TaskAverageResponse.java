package com.coolplanet.averagemonitor.task.api.dto;

public record TaskAverageResponse(String taskId, double averageDurationMillis) {
}