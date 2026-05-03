package com.coolplanet.averagemonitor.task.service;

import com.coolplanet.averagemonitor.task.domain.TaskStatistics;
import com.coolplanet.averagemonitor.task.exception.InvalidTaskRequestException;
import com.coolplanet.averagemonitor.task.exception.TaskNotFoundException;
import com.coolplanet.averagemonitor.task.exception.TaskUpdateConflictException;
import com.coolplanet.averagemonitor.task.repository.TaskStatisticsRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.locks.LockSupport;

@Service
public class TaskStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(TaskStatisticsService.class);

    private static final int MAX_RETRIES = 10;
    private static final long RETRY_BACKOFF_MILLIS = 10L;

    private final TaskStatisticsRepository taskStatisticsRepository;
    private final TransactionTemplate transactionTemplate;

    public TaskStatisticsService(TaskStatisticsRepository taskStatisticsRepository,
                                 PlatformTransactionManager transactionManager) {
        this.taskStatisticsRepository = taskStatisticsRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void recordExecution(String taskId, long durationMillis) {
        String normalizedTaskId = normalizeTaskId(taskId);
        validateDuration(durationMillis);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> recordExecutionInNewTransaction(normalizedTaskId, durationMillis));
                return;
            } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException exception) {
                log.warn("Optimistic lock conflict for taskId={} attempt={}/{}", normalizedTaskId, attempt, MAX_RETRIES);
                if (attempt == MAX_RETRIES) {
                    log.error("Max retries exhausted for taskId={}", normalizedTaskId);
                    throw new TaskUpdateConflictException(normalizedTaskId, exception);
                }
                LockSupport.parkNanos(RETRY_BACKOFF_MILLIS * attempt * 1_000_000L);
            }
        }
    }

    // From production point of view:
    // 1. We would keep task statistics as an aggregate with atomic updates, 
    // 2. Protect concurrent writes using optimistic locking plus retries.
    // 3. Can move to concurrent data structures for performance at scale, but that adds complexity around consistency and durability guarantees.
    @Transactional(readOnly = true)
    public TaskAverageSnapshot getCurrentAverage(String taskId) {
        String normalizedTaskId = normalizeTaskId(taskId);

        TaskStatistics taskStatistics = taskStatisticsRepository.findById(normalizedTaskId)
                .orElseThrow(() -> new TaskNotFoundException(normalizedTaskId));

        return new TaskAverageSnapshot(taskStatistics.getTaskId(), taskStatistics.averageDurationMillis());
    }

    @Transactional
    void recordExecutionInNewTransaction(String taskId, long durationMillis) {
        TaskStatistics taskStatistics = taskStatisticsRepository.findById(taskId)
                .orElseGet(() -> new TaskStatistics(taskId));
        taskStatistics.recordExecution(durationMillis);
        taskStatisticsRepository.saveAndFlush(taskStatistics);
    }

    private static String normalizeTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new InvalidTaskRequestException("Task identifier must not be blank.");
        }
        return taskId.trim();
    }

    private static void validateDuration(long durationMillis) {
        if (durationMillis < 0L) {
            throw new InvalidTaskRequestException("Duration must be greater than or equal to zero.");
        }
    }
}