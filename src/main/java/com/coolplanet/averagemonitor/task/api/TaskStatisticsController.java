package com.coolplanet.averagemonitor.task.api;

import com.coolplanet.averagemonitor.task.api.dto.TaskAverageResponse;
import com.coolplanet.averagemonitor.task.api.dto.TaskExecutionRequest;
import com.coolplanet.averagemonitor.task.service.TaskAverageSnapshot;
import com.coolplanet.averagemonitor.task.service.TaskStatisticsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/tasks")
public class TaskStatisticsController {

    private static final Logger log = LoggerFactory.getLogger(TaskStatisticsController.class);

    private final TaskStatisticsService taskStatisticsService;

    public TaskStatisticsController(TaskStatisticsService taskStatisticsService) {
        this.taskStatisticsService = taskStatisticsService;
    }

    // From Production point of view we can also add
    // 1. expose clear API contracts with validation
    // 2. Consistent error models
    // 3. Predictable status codes with Swagger docs
    // 4. Integrate OpenTelemetry with a collector + Prometheus for metrics and Grafana/Tempo for distributed
    //    tracing, so request latencies, error rates and task execution trends can be monitored over time
    // 5. Can also add authentication to allow only authorized personnel to publish and view data
    @PostMapping("/{taskId}/executions")
    public ResponseEntity<Void> recordExecution(@PathVariable String taskId,
                                                @Valid @RequestBody TaskExecutionRequest request) {
        log.debug("Recording execution for taskId={} durationMillis={}", taskId, request.durationMillis());
        taskStatisticsService.recordExecution(taskId, request.durationMillis());
        log.info("Execution recorded for taskId={}", taskId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{taskId}/average")
    public TaskAverageResponse getCurrentAverage(@PathVariable String taskId) {
        log.debug("Fetching average for taskId={}", taskId);
        TaskAverageSnapshot snapshot = taskStatisticsService.getCurrentAverage(taskId);
        log.debug("Average for taskId={} is {}ms", taskId, snapshot.averageDurationMillis());
        return new TaskAverageResponse(snapshot.taskId(), snapshot.averageDurationMillis());
    }
}