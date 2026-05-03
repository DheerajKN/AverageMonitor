package com.coolplanet.averagemonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AverageMonitorApplication {

// From Production point of view we can also add
// 1. Observability (metrics, health checks, structured logs)
// 2. Containerization
// 3. Graceful shutdown
// 4. Configuration via environment variables
    public static void main(String[] args) {
        SpringApplication.run(AverageMonitorApplication.class, args);
    }

}
