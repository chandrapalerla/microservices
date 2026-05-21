package com.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Data Transfer Object for User entity")
public class UserDto {
    @Schema(description = "User ID (auto-generated, null on create)", example = "1")
    private Long id;

    @Schema(description = "Optimistic lock version (for concurrent update detection)", example = "0")
    private Long version;

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must be at most 200 characters")
    @Schema(description = "User's full name", example = "John Doe", maxLength = 200)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Schema(description = "User's email address (must be valid)", example = "john@example.com")
    private String email;
}

