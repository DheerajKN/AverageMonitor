package com.coolplanet.averagemonitor.task.exception;

public class InvalidTaskRequestException extends RuntimeException {

    public InvalidTaskRequestException(String message) {
        super(message);
    }
}