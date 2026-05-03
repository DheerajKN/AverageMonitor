# Average Monitor

Average Monitor is a Spring Boot microservice that records task execution durations and returns the current average duration for each named task. It persists aggregates in a database so recorded averages remain available after restarts.

## Design Summary

- Java 21 and Spring Boot 4
- REST API with synchronous writes and reads
- Persistent aggregate model storing `taskId`, `sampleCount`, and `totalDurationMillis`
- File-backed H2 database by default for zero-setup local runs
- Optimistic locking with bounded retries to reduce lost updates under concurrent writes

## API

### Record a task execution

```bash
curl -X POST http://localhost:8080/api/tasks/email-dispatch/executions \
  -H 'Content-Type: application/json' \
  -d '{"durationMillis":120}'
```

Successful response: `200 OK`

### Get the current average

```bash
curl http://localhost:8080/api/tasks/email-dispatch/average
```

Example response:

```json
{
  "taskId": "email-dispatch",
  "averageDurationMillis": 120.0
}
```

## Error Cases

All error responses follow RFC 7807 (Problem Details) format with HTTP status code, error detail message, and request path.

### 404 – Task Not Found

When querying the average for a task with no recorded executions.

**Request:**
```bash
curl http://localhost:8080/api/tasks/unknown-task/average
```

**Response (404 Not Found):**
```json
{
  "detail": "Task 'unknown-task' has no recorded executions.",
  "instance": "/api/tasks/unknown-task/average",
  "status": 404,
  "title": "Not Found",
  "path": "/api/tasks/unknown-task/average"
}
```

### 400 – Invalid Request

**Scenario 1: Negative Duration**
```bash
curl -X POST http://localhost:8080/api/tasks/my-task/executions \
  -H 'Content-Type: application/json' \
  -d '{"durationMillis":-100}'
```

**Response (400 Bad Request):**
```json
{
  "detail": "durationMillis must be greater than or equal to zero",
  "instance": "/api/tasks/my-task/executions",
  "status": 400,
  "title": "Bad Request",
  "path": "/api/tasks/my-task/executions"
}
```

**Scenario 2: Missing Required Field**
```bash
curl -X POST http://localhost:8080/api/tasks/my-task/executions \
  -H 'Content-Type: application/json' \
  -d '{}'
```

**Response (400 Bad Request):**
```json
{
  "detail": "durationMillis is required",
  "instance": "/api/tasks/my-task/executions",
  "status": 400,
  "title": "Bad Request",
  "path": "/api/tasks/my-task/executions"
}
```

### 409 – Conflict

Returned in two scenarios:

**Scenario 1: Concurrent Modification Exceeded Retry Budget**

When 10+ concurrent threads attempt to update the same task simultaneously and retries are exhausted after 10 attempts (10ms backoff per attempt).

**Response (409 Conflict):**
```json
{
  "detail": "Task 'conflict-task' could not be updated due to concurrent modifications.",
  "instance": "/api/tasks/conflict-task/executions",
  "status": 409,
  "title": "Conflict",
  "path": "/api/tasks/conflict-task/executions"
}
```

### Trigger 409

First lower MAX_RETRIES = 10; inside TaskStatisticsService and run the script

```
python3 scripts/trigger-409.py --threads 200
```

## Validation Rules

| Field | Rule | Error Code |
|-------|------|-----------|
| `taskId` (path parameter) | Must not be blank or whitespace only | 400 |
| `durationMillis` (request body) | Must be present (not null) | 400 |
| `durationMillis` (request body) | Must be ≥ 0 | 400 |
| Concurrent writes to same task | Max 10 retry attempts over 100ms; then 409 | 409 |
| Total duration accumulation | Must not exceed `Long.MAX_VALUE` | 409 |

## Build And Test

```bash
./mvnw clean test
```

## Run Locally

```bash
./mvnw spring-boot:run
```

The default datasource is a file-backed H2 database stored under `./data`, which allows averages to survive application restarts.

## Packaging

```bash
./mvnw clean package
java -jar target/AverageMonitor-0.0.1-SNAPSHOT.jar
```

## Docker

Build the image:

```bash
docker build -t average-monitor:local .
```

Run the container directly:

```bash
docker run --rm -p 8080:8080 -v average-monitor-data:/app/data average-monitor:local
```

Run with Docker Compose:

```bash
docker compose up --build
```

The compose setup mounts a named volume at `/app/data`, so the file-backed H2 database survives container restarts.