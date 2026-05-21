package com.springbatch.writer;

import com.springbatch.entity.Employee;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmployeeWriter {

    @Bean
    public JpaItemWriter<Employee> writer(
            EntityManagerFactory emf) {

        JpaItemWriter<Employee> writer =
                new JpaItemWriter<>();

        writer.setEntityManagerFactory(emf);

        return writer;
    }
}