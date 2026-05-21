package com.springbatch.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/batch")
@Slf4j
public class BatchJobController {

    private final JobLauncher jobLauncher;
    private final Job employeeJob;

    public BatchJobController(JobLauncher jobLauncher, Job employeeJob) {
        this.jobLauncher = jobLauncher;
        this.employeeJob = employeeJob;
    }

    /**
     * Endpoint to manually trigger the employee batch job
     * GET /api/batch/start
     */
    @GetMapping("/start")
    public ResponseEntity<?> startBatchJob() {
        try {
            log.info("Starting batch job manually...");

            // Create unique job parameters to allow multiple executions
            Map<String, JobParameter<?>> params = new HashMap<>();
            params.put("startTime", new JobParameter<>(LocalDateTime.now().toString(), String.class));

            JobParameters jobParameters = new JobParameters(params);

            // Launch the job
            JobExecution execution = jobLauncher.run(employeeJob, jobParameters);

            Map<String, Object> response = new HashMap<>();
            response.put("jobId", execution.getId());
            response.put("status", execution.getStatus().toString());
            response.put("message", "Batch job started successfully!");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error starting batch job", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}

