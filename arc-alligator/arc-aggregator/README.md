# ARC Aggregator

A Spring Boot application for aggregating data from various sources (starting with Splunk) and exporting it to CSV format. The application provides a REST API to execute queries and automatically export results to configurable output locations.

## Project Purpose

The ARC Aggregator serves as a centralized data collection and export service that:
- Connects to Splunk via REST API to execute queries
- Processes and transforms query results
- Exports data to CSV format for further analysis
- Provides a scalable foundation for adding more data sources

## Code Organization

```
arc-aggregator/
├── src/main/java/com/arc/aggregator/
│   ├── ArcAggregatorApplication.java    # Main Spring Boot application
│   ├── config/
│   │   └── AppConfig.java              # Configuration beans and properties
│   ├── controller/
│   │   └── DataController.java         # REST API endpoints
│   └── service/
│       ├── SplunkService.java          # Splunk API integration
│       └── CsvWriterService.java       # CSV export functionality
├── src/main/resources/
│   └── application.yml                 # Application configuration
├── output/                             # CSV output directory
└── pom.xml                            # Maven dependencies
```

### Key Components

- **SplunkService**: Handles Splunk REST API communication using RestTemplate
- **CsvWriterService**: Manages CSV export using OpenCSV library
- **DataController**: Provides REST endpoint for data aggregation
- **AppConfig**: Centralizes configuration properties and beans

## How to Run Stage 1

### Prerequisites

- Java 21 or higher
- Maven 3.6+
- Access to a Splunk instance with REST API enabled

### Configuration

1. **Environment Variables** (recommended for production):
   ```bash
   export SPLUNK_BASE_URL="https://your-splunk-instance.com:8089"
   export SPLUNK_AUTH_TOKEN="your-splunk-auth-token"
   export SPLUNK_USERNAME="your-splunk-username"
   export SPLUNK_PASSWORD="your-splunk-password"
   export OUTPUT_DIRECTORY="/path/to/output/directory"
   ```

2. **Or modify `application.yml`** directly with your values

### Building and Running

1. **Build the project**:
   ```bash
   mvn clean package
   ```

2. **Run the application**:
   ```bash
   java -jar target/arc-aggregator-1.0.0.jar
   ```

3. **Or run with Maven**:
   ```bash
   mvn spring-boot:run
   ```

### Testing the API

1. **Health check**:
   ```bash
   curl http://localhost:8080/actuator/health
   ```

2. **Execute a Splunk query**:
   ```bash
   curl -X POST http://localhost:8080/api/aggregate \
     -H "Content-Type: application/json" \
     -d '{"query": "search index=main | head 100"}'
   ```

3. **Response example**:
   ```json
   {
     "status": "success",
     "file": "output/splunk_results_20241201_143022.csv"
   }
   ```

### Sample Splunk Queries

- **Basic search**: `search index=main | head 100`
- **Time-based**: `search index=main earliest=-1h | stats count by sourcetype`
- **Field extraction**: `search index=main | table _time, host, source, sourcetype`

## Project Extensibility

### A. Immediate Next Steps

#### Configurable Saved Searches
Move search names to `application.yml` for common queries:
```yaml
app:
  saved-searches:
    specs: "search index=main sourcetype=specs | table *"
    health: "search index=main sourcetype=health | stats count by status"
    incidents: "search index=main sourcetype=incident | table _time, severity, description"
```

#### Header Control
Support whitelist/rename mapping for CSV columns:
```yaml
app:
  csv:
    column-mapping:
      "_time": "timestamp"
      "host": "server_name"
      "source": "log_source"
    whitelist:
      - "timestamp"
      - "server_name"
      - "log_source"
      - "message"
```

#### Cursor File
Store last successful window (`.cursor`) for safe resumes:
```yaml
app:
  cursor:
    enabled: true
    file-path: ".cursor"
    window-size: "1h"
```

### B. Phase 2 – Flip Sink to DB

#### DB Sink (Postgres JSONB preferred)
Add `DbSnapshotSink` with Flyway migration:

```sql
CREATE TABLE api_snapshot (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(50) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    api_key VARCHAR(100) NOT NULL,
    snapshot_ts TIMESTAMP NOT NULL,
    doc JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_snapshot_lookup 
ON api_snapshot(platform, environment, api_key, snapshot_ts DESC);

CREATE INDEX idx_api_snapshot_doc 
ON api_snapshot USING GIN(doc);
```

**Spring profile/flag** to switch CSV → DB without code changes:
```yaml
app:
  sink:
    type: ${SINK_TYPE:csv} # csv, database, both
    database:
      enabled: false
      table: api_snapshot
```

#### State Store
Fetch "last snapshot" by key to enable diff (for change summaries):
```java
@Service
public class StateStoreService {
    public Optional<Snapshot> getLastSnapshot(String platform, String environment, String apiKey);
    public void storeSnapshot(Snapshot snapshot);
    public ChangeSummary computeDiff(Snapshot current, Snapshot previous);
}
```

### C. Additional Source Adapters

Follow the same pattern for each adapter:

#### ITSM Adapter
```java
@Service
public class ItsmAdapterService {
    public Map<String, Object> fetchData(String query);
    public void exportToCsv(Map<String, Object> data);
    public void exportToDatabase(Map<String, Object> data);
}
```

#### Central API Adapter
```java
@Service
public class CentralApiAdapterService {
    public Map<String, Object> fetchData(String endpoint, Map<String, String> params);
}
```

#### Jira Adapter
```java
@Service
public class JiraAdapterService {
    public Map<String, Object> fetchIssues(String jql);
    public Map<String, Object> fetchProjects();
}
```

#### Rally Adapter
```java
@Service
public class RallyAdapterService {
    public Map<String, Object> fetchUserStories(String projectId);
    public Map<String, Object> fetchDefects(String projectId);
}
```

#### AppSec (Traceable) Adapter
```java
@Service
public class TraceableAdapterService {
    public Map<String, Object> fetchVulnerabilities(String environment);
    public Map<String, Object> fetchScans(String projectId);
}
```

### D. Aggregation / Governance Features

#### Canonical Schema & Validation
Draft-07 JSON Schema; reject to DLQ (bad-rows file) with reasons:

```yaml
app:
  validation:
    schema-path: "schemas/canonical-schema.json"
    dlq:
      enabled: true
      file-path: "bad-rows"
      max-retries: 3
```

#### Identity & Idempotence
Key=(platform,env,api_key), event_id=sha256(...) to dedupe:

```java
@Component
public class IdempotenceService {
    public String generateEventId(Map<String, Object> data);
    public boolean isDuplicate(String eventId);
    public void markProcessed(String eventId);
}
```

#### Reduce
Keep latest per key in a window:

```java
@Service
public class ReduceService {
    public Map<String, Object> reduceByKey(List<Map<String, Object>> data, String keyField);
    public Map<String, Object> reduceByTimeWindow(List<Map<String, Object>> data, Duration window);
}
```

#### Diff
Compute change_summary vs last snapshot:

```java
@Service
public class DiffService {
    public ChangeSummary computeDiff(Snapshot current, Snapshot previous);
    public List<String> getChangedFields(Snapshot current, Snapshot previous);
    public Map<String, Object> getFieldDeltas(Snapshot current, Snapshot previous);
}
```

#### Retention
1-year baseline (DB partitions or Mongo TTL):

```yaml
app:
  retention:
    enabled: true
    duration: "1y"
    strategy: "partition" # partition, ttl, archive
    archive:
      path: "/archive"
      compression: "gzip"
```

### E. Ops & Quality

#### Metrics
Rows.raw, rows.written, run.duration.ms, run.success, errors.count:

```java
@Component
public class MetricsService {
    public void incrementRawRows(long count);
    public void incrementWrittenRows(long count);
    public void recordRunDuration(long durationMs);
    public void recordSuccess(boolean success);
    public void incrementErrorCount();
}
```

#### Security
Splunk/Jira/etc. tokens via env/secret manager:

```yaml
app:
  security:
    vault:
      enabled: true
      provider: "aws-secretsmanager" # aws-secretsmanager, hashicorp-vault, azure-keyvault
    encryption:
      enabled: true
      algorithm: "AES-256-GCM"
```

#### Testing
Unit tests for flattener, CSV writer, Splunk client; end-to-end happy path:

```bash
# Run all tests
mvn test

# Run specific test categories
mvn test -Dtest=*ServiceTest
mvn test -Dtest=*ControllerTest
mvn test -Dtest=*IntegrationTest
```

#### Backfill CLI/Endpoint
Already present; document rate limits and retries:

```yaml
app:
  backfill:
    enabled: true
    rate-limit:
      requests-per-second: 10
      max-concurrent: 5
    retry:
      max-attempts: 3
      backoff-multiplier: 2.0
      initial-delay: "1s"
```

## Development Guidelines

1. **Follow Spring Boot best practices** for configuration and dependency injection
2. **Use Lombok** to reduce boilerplate code
3. **Implement proper error handling** with meaningful error messages
4. **Add comprehensive logging** for debugging and monitoring
5. **Write unit tests** for all service methods
6. **Use environment variables** for sensitive configuration
7. **Follow REST API conventions** for endpoint design

## Contributing

1. Fork the repository
2. Create a feature branch
3. Implement your changes with tests
4. Submit a pull request with detailed description

## License

This project is licensed under the MIT License. 