package com.springbatch.reader;

import com.springbatch.entity.Employee;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class EmployeeReader {

    @Bean
    public FlatFileItemReader<Employee> reader() {

        return new FlatFileItemReaderBuilder<Employee>()
                .name("employeeReader")
                .resource(new ClassPathResource("data.csv"))
                .delimited()
                .names("id", "name", "department", "salary")
                .targetType(Employee.class)
                .build();
    }
}