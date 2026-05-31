package com.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for creating or updating a category. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @NotBlank(message = "Slug is required")
    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9-]+$",
             message = "Slug must contain only lowercase letters, digits and hyphens")
    private String slug;

    /** ID of the parent category; null for root categories. */
    private Long parentId;

    /** Whether the category is active/visible. Defaults to true on create. */
    private Boolean active;
}
