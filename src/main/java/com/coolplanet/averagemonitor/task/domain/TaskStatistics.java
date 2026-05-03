package com.coolplanet.averagemonitor.task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "task_statistics")
public class TaskStatistics {

    @Id
    @Column(name = "task_id", nullable = false, updatable = false, length = 128)
    private String taskId;

    @Column(name = "total_duration_millis", nullable = false)
    private long totalDurationMillis;

    @Column(name = "sample_count", nullable = false)
    private long sampleCount;

    @Version
    @Column(name = "version")
    private Long version;

    protected TaskStatistics() {
    }

    public TaskStatistics(String taskId) {
        this.taskId = taskId;
        this.totalDurationMillis = 0L;
        this.sampleCount = 0L;
    }

    public String getTaskId() {
        return taskId;
    }

    public long getTotalDurationMillis() {
        return totalDurationMillis;
    }

    public long getSampleCount() {
        return sampleCount;
    }

    public double averageDurationMillis() {
        if (sampleCount == 0L) {
            return 0D;
        }
        return (double) totalDurationMillis / sampleCount;
    }

    public void recordExecution(long durationMillis) {
        totalDurationMillis = Math.addExact(totalDurationMillis, durationMillis);
        sampleCount = Math.addExact(sampleCount, 1L);
    }
}