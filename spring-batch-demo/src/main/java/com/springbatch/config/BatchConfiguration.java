package com.springbatch.config;

import com.springbatch.entity.Employee;
import com.springbatch.listener.JobListener;
import com.springbatch.listener.StepListener;
import com.springbatch.processor.EmployeeProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@Slf4j
public class BatchConfiguration {

    /**
     * Define the batch step that reads from CSV, processes, and writes to DB
     */
    @Bean
    public Step employeeStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<Employee> reader,
            EmployeeProcessor processor,
            JpaItemWriter<Employee> writer,
            StepListener stepListener,
            JobListener jobListener) {

        return new StepBuilder("employeeStep", jobRepository)
                .<Employee, Employee>chunk(10, transactionManager)  // Process 10 items per transaction
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(stepListener)
                .faultTolerant()
                .skipPolicy((throwable, skipCount) -> {
                    // Log and continue on RuntimeException (e.g., invalid salary)
                    if (throwable instanceof RuntimeException) {
                        log.warn("Skipping record due to: {}", throwable.getMessage());
                        return true;
                    }
                    return false;
                })
                .build();
    }

    /**
     * Define the batch job that orchestrates the step
     */
    @Bean
    public Job employeeJob(
            JobRepository jobRepository,
            Step employeeStep,
            JobListener jobListener) {

        return new JobBuilder("employeeJob", jobRepository)
                .listener(jobListener)
                .start(employeeStep)
                .build();
    }
}

