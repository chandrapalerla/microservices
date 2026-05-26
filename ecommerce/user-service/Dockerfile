# Multi-stage build for Spring Boot application
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy JAR from builder stage
COPY --from=builder /build/target/user-service-*.jar app.jar

# Expose port
EXPOSE 2026

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:2026/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]

