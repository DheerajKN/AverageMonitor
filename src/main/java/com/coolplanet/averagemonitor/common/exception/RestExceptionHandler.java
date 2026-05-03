package com.coolplanet.averagemonitor.common.exception;

import com.coolplanet.averagemonitor.task.exception.InvalidTaskRequestException;
import com.coolplanet.averagemonitor.task.exception.TaskNotFoundException;
import com.coolplanet.averagemonitor.task.exception.TaskUpdateConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(TaskNotFoundException.class)
    public ProblemDetail handleTaskNotFound(TaskNotFoundException exception, HttpServletRequest request) {
        log.warn("Task not found: uri={} message={}", request.getRequestURI(), exception.getMessage());
        return problemDetail(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({InvalidTaskRequestException.class, MethodArgumentNotValidException.class})
    public ProblemDetail handleBadRequest(Exception exception, HttpServletRequest request) {
        String detail = exception.getMessage();
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            if (methodArgumentNotValidException.getBindingResult().getFieldError() != null) {
                detail = methodArgumentNotValidException.getBindingResult().getFieldError().getDefaultMessage();
            } else {
                detail = "Request validation failed.";
            }
        }
        log.warn("Bad request: uri={} detail={}", request.getRequestURI(), detail);
        return problemDetail(HttpStatus.BAD_REQUEST, detail, request);
    }

    @ExceptionHandler({TaskUpdateConflictException.class, ArithmeticException.class})
    public ProblemDetail handleConflict(RuntimeException exception, HttpServletRequest request) {
        log.error("Conflict error: uri={} message={}", request.getRequestURI(), exception.getMessage());
        return problemDetail(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    private static ProblemDetail problemDetail(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("path", request.getRequestURI());
        return problemDetail;
    }
}