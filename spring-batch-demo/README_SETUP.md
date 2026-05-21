# Spring Batch Demo - CSV to Database

## Overview
This Spring Batch application reads employee data from a CSV file and writes it to a MySQL database with validation and processing.

## Project Structure

```
spring-batch-demo/
├── src/main/java/com/springbatch/
│   ├── SpringBatchDemoApplication.java     # Main application entry point
│   ├── config/
│   │   └── BatchConfiguration.java         # Batch job and step configuration
│   ├── controller/
│   │   └── BatchJobController.java         # REST API to trigger batch jobs
│   ├── entity/
│   │   └── Employee.java                   # JPA entity mapped to DB table
│   ├── listener/
│   │   ├── JobListener.java                # Job lifecycle listener
│   │   └── StepListener.java               # Step lifecycle listener
│   ├── processor/
│   │   └── EmployeeProcessor.java          # Business logic: validate & transform data
│   ├── reader/
│   │   └── EmployeeReader.java             # CSV reader configuration
│   ├── repository/
│   │   └── EmployeeRepository.java         # JPA repository for DB operations
│   └── writer/
│       └── EmployeeWriter.java             # Database writer configuration
├── src/main/resources/
│   ├── application.yaml                    # Application configuration
│   └── data.csv                            # Source data file
└── pom.xml                                 # Maven dependencies
```

## Key Components

### 1. **Employee Entity** (`entity/Employee.java`)
JPA entity representing an employee record:
- `id` (Primary Key)
- `name`
- `department`
- `salary`
- `status` (set during processing)

### 2. **CSV Reader** (`reader/EmployeeReader.java`)
- Reads `data.csv` from classpath
- Maps CSV columns: `id, name, department, salary`
- Uses `FlatFileItemReader` for CSV parsing

### 3. **Employee Processor** (`processor/EmployeeProcessor.java`)
Applies business logic to each record:
- **Validation**: Throws exception if salary < 0
- **Transformation**: Converts department to uppercase
- **Status**: Sets status to "PROCESSED"
- Records with invalid data are skipped (fault tolerance)

### 4. **Database Writer** (`writer/EmployeeWriter.java`)
- Uses `JpaItemWriter` to persist data
- Writes processed employees to MySQL database

### 5. **Batch Configuration** (`config/BatchConfiguration.java`)
- Defines the batch `Step`: wires reader → processor → writer
- Chunk size: 10 (processes 10 records per transaction)
- Fault tolerance: Skips invalid records and continues
- Defines the `Job`: orchestrates the step with listeners

### 6. **REST Controller** (`controller/BatchJobController.java`)
- Endpoint: `GET /api/batch/start`
- Manually triggers the batch job
- Returns job execution status

---

## Setup Instructions

### Prerequisites
- Java 25+
- Maven 3.6+
- MySQL 8.0+

### 1. Create Database
```sql
CREATE DATABASE batch_demo;
USE batch_demo;
```

### 2. Configure Database Connection
Edit `src/main/resources/application.yaml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:30036/batch_demo
    username: root
    password: root
```

### 3. Build the Project
```bash
cd spring-batch-demo
./mvnw clean package
```

### 4. Run the Application
```bash
java -jar target/spring-batch-demo-0.0.1-SNAPSHOT.jar
```

Or with Maven:
```bash
./mvnw spring-boot:run
```

---

## How It Works

### Batch Job Execution Flow
1. **Application Startup**
   - Spring Boot initializes the application
   - Batch jobs are auto-loaded (enabled in `application.yaml`)
   - If `spring.batch.job.enabled=true`, the job runs on startup

2. **Read Step**
   - `EmployeeReader` reads from `data.csv`
   - Each line is parsed and mapped to `Employee` object

3. **Process Step**
   - `EmployeeProcessor.process()` is called for each employee
   - Validates salary (>= 0)
   - Converts department to uppercase
   - Sets status to "PROCESSED"
   - Skips employees with invalid salary

4. **Write Step**
   - Valid processed employees are written to the database
   - `JpaItemWriter` performs batch inserts (10 at a time)

5. **Job Completion**
   - `JobListener.afterJob()` logs the completion
   - Results are stored in `SPRING_BATCH_*` metadata tables

### Sample Data Flow
```
Input CSV (data.csv):
1,John,IT,50000
2,Ram,HR,45000
3,Smith,Admin,-100        ← Will be skipped (invalid salary)
4,David,Finance,70000
5,Alex,IT,80000

After Processing (inserted into DB):
id=1, name=John, department=IT, salary=50000, status=PROCESSED
id=2, name=Ram, department=HR, salary=45000, status=PROCESSED
id=4, name=David, department=FINANCE, salary=70000, status=PROCESSED
id=5, name=Alex, department=IT, salary=80000, status=PROCESSED
```

---

## Running the Batch Job

### Option 1: Auto-run on Startup (Default)
- Edit `application.yaml` and set `spring.batch.job.enabled: true`
- The job runs automatically when the application starts

### Option 2: Manual Trigger via REST API
- Start the application
- Call: `GET http://localhost:2028/api/batch/start`
- Response example:
```json
{
  "jobId": 1,
  "status": "STARTING",
  "message": "Batch job started successfully!"
}
```

### Option 3: Run with Custom Configuration
```bash
java -jar target/spring-batch-demo-0.0.1-SNAPSHOT.jar \
  --spring.batch.job.enabled=true \
  --spring.datasource.url=jdbc:mysql://localhost:3306/batch_demo
```

---

## Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 2028 | Application port |
| `spring.datasource.url` | jdbc:mysql://localhost:30036/batch_demo | MySQL connection |
| `spring.jpa.hibernate.ddl-auto` | update | Auto-create/update tables |
| `spring.batch.jdbc.initialize-schema` | always | Initialize batch metadata tables |
| `spring.batch.job.enabled` | true | Run job on startup |
| `logging.level.org.springframework.batch` | INFO | Batch logging level |

---

## Database Schema

### EMPLOYEE Table (Auto-created)
```sql
CREATE TABLE employee (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255),
    department VARCHAR(255),
    salary DOUBLE,
    status VARCHAR(255)
);
```

### Batch Metadata Tables (Auto-created)
- `SPRING_BATCH_JOB_INSTANCE` - Job instances
- `SPRING_BATCH_JOB_EXECUTION` - Job executions
- `SPRING_BATCH_STEP_EXECUTION` - Step executions
- `SPRING_BATCH_JOB_EXECUTION_PARAMS` - Job parameters

---

## Monitoring & Logs

### View Batch Job Status
```sql
SELECT * FROM SPRING_BATCH_JOB_EXECUTION;
SELECT * FROM SPRING_BATCH_STEP_EXECUTION;
SELECT * FROM employee;
```

### Expected Logs
```
INFO  c.springbatch.listener.JobListener - JOB STARTED
INFO  c.springbatch.listener.StepListener - STEP STARTED
INFO  c.springbatch.processor.EmployeeProcessor - Processing employee: John
WARN  c.springbatch.config.BatchConfiguration - Skipping record due to: Invalid salary
INFO  c.springbatch.listener.StepListener - STEP COMPLETED
INFO  c.springbatch.listener.JobListener - JOB ENDED
```

---

## Error Handling

### Fault Tolerance
- **Skip Policy**: Records with invalid salary (< 0) are logged and skipped
- **Logging**: Warnings are logged for each skipped record
- **Transaction**: Batch commits every 10 records (configurable chunk size)

### Troubleshooting

| Issue | Solution |
|-------|----------|
| Database connection error | Verify MySQL is running and URL is correct |
| Job not running on startup | Set `spring.batch.job.enabled: true` |
| CSV file not found | Ensure `data.csv` is in `/src/main/resources/` |
| Records not in DB | Check `EMPLOYEE` table and batch execution logs |

---

## Advanced Customization

### Change Chunk Size
Edit `BatchConfiguration.java`:
```java
.<Employee, Employee>chunk(20, transactionManager)  // Process 20 items per transaction
```

### Add More Listeners
Implement `ItemReadListener`, `ItemProcessListener`, or `ItemWriteListener` and add to step:
```java
.listener(yourListener)
```

### Skip/Retry Logic
Modify skip policy in `BatchConfiguration`:
```java
.skipPolicy((throwable, skipCount) -> {
    // Custom logic for different exception types
    return skipCount < 10;  // Skip up to 10 errors
})
```

### Include Header Row in CSV
Update `EmployeeReader.java`:
```java
.linesToSkip(1)  // Skip first line (headers)
```

---

## Dependencies

- **Spring Boot Starter Batch** - Batch processing framework
- **Spring Boot Starter Data JPA** - Database persistence
- **Spring Boot Starter Web** - REST API support
- **MySQL Connector/J** - JDBC driver
- **Lombok** - Reduce boilerplate code
- **Spring Batch Test** - Testing utilities

---

## Testing

Run unit tests:
```bash
./mvnw test
```

Run integration tests:
```bash
./mvnw verify
```

---

## License & Notes
This is a demo project to showcase Spring Batch CSV-to-Database workflow.

