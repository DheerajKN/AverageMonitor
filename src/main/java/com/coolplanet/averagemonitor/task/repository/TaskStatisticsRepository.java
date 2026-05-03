package com.coolplanet.averagemonitor.task.repository;

import com.coolplanet.averagemonitor.task.domain.TaskStatistics;

import org.springframework.data.jpa.repository.JpaRepository;

// From Production point of view we can also add
// 1. For availability and performance:
//  a. I would use connection pooling, 
//  b. index task IDs,
// 2. retry/backoff behavior under contention.
// 3. Cache for after average computation is done to reduce DB load for frequent reads.
public interface TaskStatisticsRepository extends JpaRepository<TaskStatistics, String> {
}