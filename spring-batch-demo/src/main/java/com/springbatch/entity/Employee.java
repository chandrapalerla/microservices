package com.springbatch.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class Employee {

    @Id
    private Long id;

    private String name;

    private String department;

    private Double salary;

    private String status;
}