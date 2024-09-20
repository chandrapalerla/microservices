# Use an official OpenJDK 21 runtime as a parent image
FROM openjdk:21-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the JAR file
COPY target/micro-recipient-0.0.1-SNAPSHOT.jar

# Expose the port the app runs on
EXPOSE 2025

# Run the application
ENTRYPOINT ["java", "-jar", "micro-recipient-0.0.1-SNAPSHOT.jar"]
