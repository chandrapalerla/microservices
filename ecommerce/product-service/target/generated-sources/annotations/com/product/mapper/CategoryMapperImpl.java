package com.product.mapper;

import com.product.dto.response.CategoryResponse;
import com.product.entity.Category;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-28T19:23:25+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 25.0.2 (Oracle Corporation)"
)
@Component
public class CategoryMapperImpl implements CategoryMapper {

    @Override
    public CategoryResponse toResponse(Category category) {
        if ( category == null ) {
            return null;
        }

        CategoryResponse.CategoryResponseBuilder categoryResponse = CategoryResponse.builder();

        categoryResponse.parentId( categoryParentId( category ) );
        categoryResponse.parentName( categoryParentName( category ) );
        categoryResponse.id( category.getId() );
        categoryResponse.name( category.getName() );
        categoryResponse.description( category.getDescription() );
        categoryResponse.slug( category.getSlug() );
        categoryResponse.active( category.isActive() );
        categoryResponse.createdAt( category.getCreatedAt() );

        categoryResponse.productCount( 0 );

        return categoryResponse.build();
    }

    private Long categoryParentId(Category category) {
        if ( category == null ) {
            return null;
        }
        Category parent = category.getParent();
        if ( parent == null ) {
            return null;
        }
        Long id = parent.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }

    private String categoryParentName(Category category) {
        if ( category == null ) {
            return null;
        }
        Category parent = category.getParent();
        if ( parent == null ) {
            return null;
        }
        String name = parent.getName();
        if ( name == null ) {
            return null;
        }
        return name;
    }
}
