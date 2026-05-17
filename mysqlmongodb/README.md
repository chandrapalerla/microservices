# mysqlmongodb

Lightweight Spring Boot sample combining MySQL (JPA) and MongoDB (Spring Data MongoDB).

## Application entry point: `src/main/java/com/mysqlmongodb/MysqlmongodbApplication.java`
2026
## Checklist (what I'll add to this repository)
- Project overview and purpose
- Prerequisites (JDK, Maven, Docker optional)
- How to configure and run locally (Windows PowerShell examples)
- Common errors and troubleshooting steps (database connection errors, missing beans)
- Helpful commands (build, run, start DBs, docker-compose)

---

## Overview
This project demonstrates a simple microservice that uses:
- MySQL via Spring Data JPA for `User` entities
- MongoDB via Spring Data MongoDB for `Order` documents

The service exposes CRUD operations through controllers and uses repositories located under `com.mysqlmongodb.mysql.repository` and `com.mysqlmongodb.mongodb.repository`.

## Prerequisites
- Java (JDK) compatible with the project. The `pom.xml` currently declares `java.version=25` — ensure you have a compatible JDK installed.
- Maven (to build and run the project)
- MySQL server (default connection: `jdbc:mysql://localhost:3306/user_db`)
- MongoDB server (default connection: `mongodb://localhost:27017/order_db`)
- (Optional) Docker & Docker Compose to run DBs in containers

## Configuration
Main configuration lives in `src/main/resources/application.properties`.
Important properties (defaults in this repo):

- MySQL
```
spring.datasource.url=jdbc:mysql://localhost:3306/user_db
spring.datasource.username=root
spring.datasource.password=root123
```

- MongoDB
```
spring.data.mongodb.uri=mongodb://localhost:27017/order_db
spring.data.mongodb.lazy-initialization=true
```

Other resilience-related properties were added (HikariCP settings, logging, lazy init). If you need different hosts/credentials, update `application.properties` or use a Spring profile.

## Build and run (Windows PowerShell)
From the project root (`C:\git-hub\microservices\mysqlmongodb`) run:

```powershell
# Build
mvn clean package

# Run using Maven
mvn spring-boot:run

# Or run the jar after building
java -jar target\mysqlmongodb-0.0.1-SNAPSHOT.jar
```

If you prefer the short compile-only check:
```powershell
mvn clean compile
```

## Starting databases on Windows
If you have MySQL/MongoDB installed as services, use PowerShell (run as Administrator):

```powershell
# Start MySQL service (service name may be MySQL, MySQL80, etc.)
Get-Service MySQL* | Start-Service

# Start MongoDB service (if installed as a service named MongoDB)
net start MongoDB
```

If you installed MongoDB manually (no service), start `mongod.exe` with a data path:

```powershell
# Example - adjust path based on your installation
& "C:\Program Files\MongoDB\Server\6.0\bin\mongod.exe" --dbpath "C:\data\db"
```

If you don't want to install DBs locally, use Docker Compose (recommended for development). Create a `docker-compose.yml` (example below) and run `docker compose up -d`.

Example `docker-compose.yml` snippet (copy into a file and run):

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: user_db
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  mongo:
    image: mongo:6.0
    ports:
      - "27017:27017"
    volumes:
      - mongo_data:/data/db

volumes:
  mysql_data:
  mongo_data:
```

Start with:

```powershell
docker compose up -d
```

## Common errors & fixes
1. MongoSocketOpenException (Connection refused)
   - Symptom: stacktrace mentioning `com.mongodb.MongoSocketOpenException` or `Connection refused`.
   - Fix: Ensure MongoDB is running on the host:port defined in `spring.data.mongodb.uri` (default `localhost:27017`). If using Docker, ensure ports are forwarded and reachable.

2. JDBCConnectionException / Communications link failure
   - Symptom: `org.hibernate.exception.JDBCConnectionException: unable to obtain isolated JDBC connection` or `Communications link failure`.
   - Fix: Ensure MySQL is running and listening on `localhost:3306` and that credentials and database exist. You can start MySQL as a Windows service or run a Docker MySQL container.

3. Bean not found for repository (e.g. `OrderRepository`)
   - Symptom: `Parameter 0 of constructor in ... required a bean of type 'com.mysqlmongodb.mongodb.repository.OrderRepository' that could not be found.`
   - Fixes applied in this repo:
     - Ensure repository packages are scanned: `@EnableMongoRepositories(basePackages = "com.mysqlmongodb.mongodb.repository")` and `@EnableJpaRepositories(basePackages = "com.mysqlmongodb.mysql.repository")` in `src/main/java/com/mysqlmongodb/config/*.java`.
     - Use constructor injection (Lombok `@RequiredArgsConstructor`) and avoid mixing `@Autowired` on final fields.

4. Application not starting due to `main` method
   - Symptom: JVM cannot find `main` or errors on startup.
   - Fix: Ensure `public static void main(String[] args)` exists in `MysqlmongodbApplication`.

## Troubleshooting tips
- Check the full application log (console output) for the first error — Spring often fails fast and subsequent logs are follow-ons.
- Increase logging for DB/hibernate to inspect SQL and connection attempts:
```
logging.level.org.hibernate.SQL=DEBUG
logging.level.com.mysql.cj.jdbc=DEBUG
```
- Validate connectivity from your machine:
  - Test MySQL connection with a client (MySQL Workbench, mysql CLI) to `localhost:3306`.
  - Test MongoDB with `mongosh` or a MongoDB client to `localhost:27017`.

## Development notes
- Repositories are under:
  - `com.mysqlmongodb.mysql.repository` (JPA)
  - `com.mysqlmongodb.mongodb.repository` (MongoDB)
- Services use Lombok `@RequiredArgsConstructor` for constructor-based dependency injection. If Lombok is not enabled in your IDE, enable annotation processing.
- The `pom.xml` contains Spring Boot and dependencies for Spring Data JPA, Spring Data MongoDB, Web, and springdoc OpenAPI.

## If you want me to help further
- I can add a `docker-compose.yml` directly to the repo and a small script to initialize the databases.
- I can add separate `application-dev.properties` and `application-docker.properties` profiles for easier local development.

---

Happy coding! If you want, I can now add a `docker-compose.yml` and a `scripts/` folder with helper PowerShell scripts to start the environment automatically.

## API Documentation
The project includes springdoc OpenAPI for API documentation. Once the application is running, you can access the Swagger UI at:
http://localhost:2026/swagger-ui/index.html