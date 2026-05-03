package com.coolplanet.averagemonitor.task.exception;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(String taskId) {
        super("Task '" + taskId + "' has no recorded executions.");
    }
}