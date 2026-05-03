package com.coolplanet.averagemonitor.task.service;

public record TaskAverageSnapshot(String taskId, double averageDurationMillis) {
}