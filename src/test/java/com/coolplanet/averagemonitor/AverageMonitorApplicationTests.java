package com.coolplanet.averagemonitor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.coolplanet.averagemonitor.task.domain.TaskStatistics;
import com.coolplanet.averagemonitor.task.repository.TaskStatisticsRepository;
import com.coolplanet.averagemonitor.task.service.TaskAverageSnapshot;
import com.coolplanet.averagemonitor.task.service.TaskStatisticsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:average-monitor-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AverageMonitorApplicationTests {

    private static final String EXECUTIONS_ENDPOINT = "/api/tasks/email-dispatch/executions";
    private static final String AVERAGE_ENDPOINT = "/api/tasks/email-dispatch/average";
    private static final String RESTART_TASK_ID = "billing-job";
    private static final String CONCURRENT_TASK_ID = "concurrent-job";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TaskStatisticsRepository taskStatisticsRepository;

    @Autowired
    private TaskStatisticsService taskStatisticsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
    }
// From Production point of view we can also add
// 1. Add layered testing: unit tests for business rules, 
// 2. Integration tests for API and persistence,
// 3. Concurrency tests for race conditions
    @Test
    void recordsExecutionAndReturnsAverage() throws Exception {
        taskStatisticsRepository.deleteAll();

        mockMvc.perform(post(EXECUTIONS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"durationMillis":100}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post(EXECUTIONS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"durationMillis":200}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(AVERAGE_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("email-dispatch"))
                .andExpect(jsonPath("$.averageDurationMillis").value(150.0));
    }

    @Test
    void rejectsNegativeDuration() throws Exception {
        mockMvc.perform(post(EXECUTIONS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"durationMillis":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("durationMillis must be greater than or equal to zero"));
    }

    @Test
    void returnsNotFoundWhenTaskHasNoSamples() throws Exception {
        mockMvc.perform(get("/api/tasks/unknown/average"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Task 'unknown' has no recorded executions."));
    }

    @Test
    void preservesRecordedAverageAcrossContextRestart() {
        Path databasePath = Path.of("target", "restart-persistence", "average-monitor-db");
        String databaseUrl = "jdbc:h2:file:" + databasePath.toAbsolutePath() + ";AUTO_SERVER=FALSE";

        try (ConfigurableApplicationContext context = applicationContext(databaseUrl)) {
            TaskStatisticsService restartedService = context.getBean(TaskStatisticsService.class);
            restartedService.recordExecution(RESTART_TASK_ID, 120L);
            restartedService.recordExecution(RESTART_TASK_ID, 180L);
        }

        try (ConfigurableApplicationContext context = applicationContext(databaseUrl)) {
            TaskStatisticsService restartedService = context.getBean(TaskStatisticsService.class);
            TaskAverageSnapshot snapshot = restartedService.getCurrentAverage(RESTART_TASK_ID);
            assertThat(snapshot.taskId()).isEqualTo(RESTART_TASK_ID);
            assertThat(snapshot.averageDurationMillis()).isEqualTo(150.0D);
        }
    }

    @Test
    void handlesConcurrentWritesForTheSameTask() throws Exception {
        taskStatisticsRepository.deleteAll();

        int workers = 8;
        int writesPerWorker = 20;
        int durationMillis = 25;
        ExecutorService executorService = Executors.newFixedThreadPool(workers);
        CountDownLatch readyLatch = new CountDownLatch(workers);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executorService.submit(() -> {
                    readyLatch.countDown();
                    assertThat(startLatch.await(5, TimeUnit.SECONDS)).isTrue();
                    for (int write = 0; write < writesPerWorker; write++) {
                        taskStatisticsService.recordExecution(CONCURRENT_TASK_ID, durationMillis);
                    }
                    return null;
                }));
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executorService.shutdownNow();
        }

        TaskStatistics statistics = taskStatisticsRepository.findById(CONCURRENT_TASK_ID).orElseThrow();
        assertThat(statistics.getSampleCount()).isEqualTo((long) workers * writesPerWorker);
        assertThat(statistics.getTotalDurationMillis()).isEqualTo((long) workers * writesPerWorker * durationMillis);
        assertThat(statistics.averageDurationMillis()).isEqualTo((double) durationMillis);
    }

    private static ConfigurableApplicationContext applicationContext(String databaseUrl) {
        return new SpringApplicationBuilder(AverageMonitorApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + databaseUrl,
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.jpa.hibernate.ddl-auto=update"
                )
                .run();
    }

}
