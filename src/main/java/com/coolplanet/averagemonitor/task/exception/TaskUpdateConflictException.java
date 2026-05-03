package com.coolplanet.averagemonitor.task.exception;

public class TaskUpdateConflictException extends RuntimeException {

    public TaskUpdateConflictException(String taskId, Throwable cause) {
        super("Task '" + taskId + "' could not be updated due to concurrent modifications.", cause);
    }
}