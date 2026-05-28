package com.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Projection of the user returned by user-service /api/v1/users/{id}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private Long version;
    private String name;
    private String email;
}
