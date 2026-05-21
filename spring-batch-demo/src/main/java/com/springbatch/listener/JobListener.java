package com.springbatch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JobListener
        implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("JOB STARTED");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("JOB ENDED");
    }
}