package com.product.mapper;

import com.product.dto.response.CategoryResponse;
import com.product.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct compile-time mapper: Category ↔ CategoryResponse.
 *
 * productCount is intentionally ignored here — it is set by CategoryService
 * after a separate COUNT query (it is not a field on the Category entity).
 *
 * parentId / parentName are mapped from the nullable parent association.
 * MapStruct handles null parents safely (returns null for both fields).
 */
@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "parentId",   source = "parent.id")
    @Mapping(target = "parentName", source = "parent.name")
    @Mapping(target = "productCount", constant = "0")
    CategoryResponse toResponse(Category category);
}
