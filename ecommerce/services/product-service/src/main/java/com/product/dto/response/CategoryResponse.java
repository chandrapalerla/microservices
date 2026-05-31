package com.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Category DTO returned to clients. productCount is set by CategoryService (not the mapper). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String slug;
    private Long parentId;
    private String parentName;
    private boolean active;

    /** Number of products (any status) associated with this category. Computed by service. */
    private int productCount;

    private LocalDateTime createdAt;
}
