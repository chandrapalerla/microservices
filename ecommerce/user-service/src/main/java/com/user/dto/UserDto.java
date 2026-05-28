package com.user.dto;

import com.user.enums.UserRole;
import com.user.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Data Transfer Object for User")
public class UserDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "User ID (auto-generated, null on create)", example = "1")
    private Long id;

    @Schema(description = "Optimistic lock version (send current value on PUT to detect conflicts)", example = "0")
    private Long version;

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must be at most 200 characters")
    @Schema(description = "User's full display name", example = "John Doe")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Schema(description = "Email address — unique across all users", example = "john@example.com")
    private String email;

    @Pattern(regexp = "^[+\\d][\\d\\s\\-().]{6,19}$",
             message = "Phone must be 7–20 chars, digits/spaces/+-()  only")
    @Schema(description = "Contact phone number (optional)", example = "+91-9876543210")
    private String phone;

    @Schema(description = "Account role: USER or ADMIN", example = "USER")
    private UserRole role;

    @Schema(description = "Account status: ACTIVE, INACTIVE, or SUSPENDED", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Timestamp of account creation (read-only)", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp of last update (read-only)", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime updatedAt;
}
