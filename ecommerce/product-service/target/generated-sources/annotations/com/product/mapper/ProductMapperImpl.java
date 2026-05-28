package com.product.mapper;

import com.product.dto.response.ProductResponse;
import com.product.dto.response.ProductSummaryResponse;
import com.product.entity.Category;
import com.product.entity.Product;
import javax.annotation.processing.Generated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-28T19:23:25+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 25.0.2 (Oracle Corporation)"
)
@Component
public class ProductMapperImpl implements ProductMapper {

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public ProductResponse toResponse(Product product) {
        if ( product == null ) {
            return null;
        }

        ProductResponse.ProductResponseBuilder productResponse = ProductResponse.builder();

        productResponse.id( product.getId() );
        productResponse.sku( product.getSku() );
        productResponse.name( product.getName() );
        productResponse.description( product.getDescription() );
        productResponse.brand( product.getBrand() );
        productResponse.price( product.getPrice() );
        productResponse.originalPrice( product.getOriginalPrice() );
        productResponse.stockQuantity( product.getStockQuantity() );
        productResponse.reservedQuantity( product.getReservedQuantity() );
        productResponse.lowStockThreshold( product.getLowStockThreshold() );
        productResponse.status( product.getStatus() );
        productResponse.thumbnailUrl( product.getThumbnailUrl() );
        productResponse.weight( product.getWeight() );
        productResponse.category( categoryMapper.toResponse( product.getCategory() ) );
        productResponse.version( product.getVersion() );
        productResponse.createdAt( product.getCreatedAt() );
        productResponse.updatedAt( product.getUpdatedAt() );

        productResponse.discountPercent( computeDiscountPercent(product) );
        productResponse.stockStatus( computeStockStatus(product) );

        return productResponse.build();
    }

    @Override
    public ProductSummaryResponse toSummary(Product product) {
        if ( product == null ) {
            return null;
        }

        ProductSummaryResponse.ProductSummaryResponseBuilder productSummaryResponse = ProductSummaryResponse.builder();

        productSummaryResponse.categoryName( productCategoryName( product ) );
        productSummaryResponse.id( product.getId() );
        productSummaryResponse.sku( product.getSku() );
        productSummaryResponse.name( product.getName() );
        productSummaryResponse.brand( product.getBrand() );
        productSummaryResponse.price( product.getPrice() );
        productSummaryResponse.originalPrice( product.getOriginalPrice() );
        productSummaryResponse.stockQuantity( product.getStockQuantity() );
        productSummaryResponse.status( product.getStatus() );
        productSummaryResponse.thumbnailUrl( product.getThumbnailUrl() );

        productSummaryResponse.discountPercent( computeDiscountPercent(product) );
        productSummaryResponse.stockStatus( computeStockStatus(product) );

        return productSummaryResponse.build();
    }

    private String productCategoryName(Product product) {
        if ( product == null ) {
            return null;
        }
        Category category = product.getCategory();
        if ( category == null ) {
            return null;
        }
        String name = category.getName();
        if ( name == null ) {
            return null;
        }
        return name;
    }
}
