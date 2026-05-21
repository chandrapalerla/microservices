package com.springbatch;

import com.springbatch.entity.Employee;
import com.springbatch.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.batch.job.enabled=false"
})
public class SpringBatchDemoApplicationTests {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job employeeJob;

    @Autowired
    private EmployeeRepository employeeRepository;

    @BeforeEach
    public void setUp() {
        employeeRepository.deleteAll();
    }

    /**
     * Test 1: Verify batch job completes successfully
     */
    @Test
    public void testBatchJobCompletion() throws Exception {
        Map<String, JobParameter<?>> params = new HashMap<>();
        params.put("startTime", new JobParameter<>(LocalDateTime.now().toString(), String.class));
        JobParameters jobParameters = new JobParameters(params);

        JobExecution execution = jobLauncher.run(employeeJob, jobParameters);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertNotNull(execution.getId());
    }

    /**
     * Test 2: Verify all valid employees are inserted into database
     */
    @Test
    public void testEmployeeDataLoaded() throws Exception {
        Map<String, JobParameter<?>> params = new HashMap<>();
        params.put("startTime", new JobParameter<>(LocalDateTime.now().toString(), String.class));
        JobParameters jobParameters = new JobParameters(params);

        JobExecution execution = jobLauncher.run(employeeJob, jobParameters);
        List<Employee> employees = employeeRepository.findAll();

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(4, employees.size());
    }

    /**
     * Test 3: Verify invalid salary records are skipped
     */
    @Test
    public void testInvalidSalarySkipped() throws Exception {
        Map<String, JobParameter<?>> params = new HashMap<>();
        params.put("startTime", new JobParameter<>(LocalDateTime.now().toString(), String.class));
        JobParameters jobParameters = new JobParameters(params);

        JobExecution execution = jobLauncher.run(employeeJob, jobParameters);
        Employee smith = employeeRepository.findById(3L).orElse(null);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertNull(smith, "Employee with invalid salary should be skipped");
    }

    /**
     * Test 4: Verify data transformation (department uppercase)
     */
    @Test
    public void testDepartmentTransformation() throws Exception {
        Map<String, JobParameter<?>> params = new HashMap<>();
        params.put("startTime", new JobParameter<>(LocalDateTime.now().toString(), String.class));
        JobParameters jobParameters = new JobParameters(params);

        JobExecution execution = jobLauncher.run(employeeJob, jobParameters);
        Employee david = employeeRepository.findById(4L).orElse(null);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertNotNull(david);
        assertEquals("FINANCE", david.getDepartment());
        assertEquals("PROCESSED", david.getStatus());
    }

    /**
     * Test 5: Verify write count
     */
    @Test
    public void testWriteCount() throws Exception {
        Map<String, JobParameter<?>> params = new HashMap<>();
        params.put("startTime", new JobParameter<>(LocalDateTime.now().toString(), String.class));
        JobParameters jobParameters = new JobParameters(params);

        JobExecution execution = jobLauncher.run(employeeJob, jobParameters);
        long writeCount = execution.getStepExecutions().stream()
                .findFirst()
                .map(se -> se.getWriteCount())
                .orElse(0L);

        assertEquals(4L, writeCount, "Should write 4 records (1 skipped)");
    }

}
