package com.springbatch.processor;

import com.springbatch.entity.Employee;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class EmployeeProcessor
        implements ItemProcessor<Employee, Employee> {

    @Override
    public Employee process(Employee employee) {

        if (employee.getSalary() < 0) {
            throw new RuntimeException("Invalid salary");
        }

        employee.setDepartment(
                employee.getDepartment().toUpperCase()
        );

        employee.setStatus("PROCESSED");

        return employee;
    }
}