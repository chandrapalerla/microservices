# Spring Batch Demo - Quick Start Guide

## ✅ Project Generated Successfully!

Your complete Spring Batch application is ready to run. Here's what was created:

### 📁 Files Created/Updated:
1. **BatchConfiguration.java** - Main batch job configuration
2. **BatchJobController.java** - REST API to trigger jobs
3. **SpringBatchDemoApplicationTests.java** - Comprehensive test suite
4. **README_SETUP.md** - Detailed documentation

### 🚀 Quick Start (3 Steps)

#### Step 1: Ensure Database is Running
```powershell
# MySQL should be running at localhost:30036
# Database: batch_demo
# User: root
# Password: root

# Verify connection:
sqlcmd -S localhost:30036 -U root -P root -q "SELECT 1"
```

#### Step 2: Start the Application
```powershell
# Navigate to project directory
cd C:\git-hub\microservices\spring-batch-demo

# Option A: Run with Maven
.\mvnw.cmd spring-boot:run

# Option B: Run the built JAR directly
java -jar target\spring-batch-demo-0.0.1-SNAPSHOT.jar

# Option C: Run without auto-executing the batch (disable job)
java -jar target\spring-batch-demo-0.0.1-SNAPSHOT.jar --spring.batch.job.enabled=false
```

#### Step 3: Verify Data Insertion
```powershell
# After app starts, check the database:

# Using MySQL CLI
mysql -h localhost -P 30036 -u root -proot batch_demo -e "SELECT * FROM employee;"

# Expected Output (4 records, 1 skipped due to invalid salary):
# +----+-------+----------+--------+-----------+
# | id | name  | department | salary | status    |
# +----+-------+----------+--------+-----------+
# | 1  | John  | IT        | 50000  | PROCESSED |
# | 2  | Ram   | HR        | 45000  | PROCESSED |
# | 4  | David | FINANCE   | 70000  | PROCESSED |
# | 5  | Alex  | IT        | 80000  | PROCESSED |
# +----+-------+----------+--------+-----------+
```

---

## 🔄 How to Trigger the Batch Job Manually

### Option 1: Auto-run on Startup (Default)
The batch job runs automatically when the application starts:
```yaml
# In application.yaml
spring.batch.job.enabled: true
```

### Option 2: REST API Endpoint
```bash
curl http://localhost:2028/api/batch/start

# Response:
# {
#   "jobId": 1,
#   "status": "STARTING",
#   "message": "Batch job started successfully!"
# }
```

### Option 3: Disable Auto-run & Run Later
```powershell
# Start without running the job
java -jar target\spring-batch-demo-0.0.1-SNAPSHOT.jar \
  --spring.batch.job.enabled=false

# Then trigger via REST API:
curl http://localhost:2028/api/batch/start
```

---

## 📊 Monitor Batch Execution

### Check Batch Metadata (via SQL)
```sql
-- View all job executions
SELECT * FROM SPRING_BATCH_JOB_EXECUTION;

-- View step executions
SELECT * FROM SPRING_BATCH_STEP_EXECUTION;

-- See processed data
SELECT * FROM employee;

-- Count processed vs skipped
SELECT COUNT(*) as total_read FROM SPRING_BATCH_STEP_EXECUTION 
WHERE STEP_NAME='employeeStep';
```

### View Application Logs
The console will show:
```
INFO  c.springbatch.listener.JobListener - JOB STARTED
INFO  c.springbatch.listener.StepListener - STEP STARTED
WARN  c.springbatch.config.BatchConfiguration - Skipping record due to: Invalid salary
INFO  c.springbatch.listener.StepListener - STEP COMPLETED
INFO  c.springbatch.listener.JobListener - JOB ENDED
```

---

## 🧪 Run Tests

```powershell
# Run all tests
.\mvnw.cmd test

# Run specific test
.\mvnw.cmd test -Dtest=SpringBatchDemoApplicationTests

# Skip tests during build
.\mvnw.cmd clean package -DskipTests
```

---

## 📝 Configuration Options

Edit `src/main/resources/application.yaml`:

```yaml
server:
  port: 2028  # Change application port

spring:
  datasource:
    url: jdbc:mysql://HOSTNAME:PORT/DATABASE
    username: USER
    password: PASSWORD
  
  batch:
    job:
      enabled: true/false  # Auto-run on startup
```

---

## 🔧 Component Overview

| Component | File | Purpose |
|-----------|------|---------|
| **Entity** | `Employee.java` | Database model |
| **Reader** | `EmployeeReader.java` | CSV file reader |
| **Processor** | `EmployeeProcessor.java` | Validation & transformation |
| **Writer** | `EmployeeWriter.java` | Database writer |
| **Job Config** | `BatchConfiguration.java` | Batch job definition |
| **Controller** | `BatchJobController.java` | REST API |
| **Listeners** | `JobListener.java`, `StepListener.java` | Lifecycle monitoring |

---

## ⚙️ Data Flow

```
data.csv (5 records)
    ↓
[EmployeeReader] → reads CSV
    ↓
[EmployeeProcessor] → validates & transforms
    ├─ ✅ Valid records: 4
    ├─ ❌ Invalid: 1 (salary < 0, skipped with warning)
    ↓
[EmployeeWriter] → writes to DB
    ↓
employee table (4 records inserted)
```

---

## 🐛 Troubleshooting

| Issue | Solution |
|-------|----------|
| "Cannot connect to MySQL" | Verify MySQL is running on localhost:30036 |
| "data.csv not found" | Ensure file is in `/src/main/resources/` |
| "Table not created" | Check `spring.jpa.hibernate.ddl-auto=update` |
| "Job not running" | Set `spring.batch.job.enabled=true` or call REST API |
| "Port 2028 already in use" | Change `server.port` or kill process using the port |

---

## 📞 Support

For detailed documentation, see: `README_SETUP.md`


