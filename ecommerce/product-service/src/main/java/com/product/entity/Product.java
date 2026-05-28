package com.product.entity;

import com.product.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Core product entity — represents a single catalogue item.
 *
 * Notes:
 *  - implements Serializable + serialVersionUID — required for Hazelcast Java serialization
 *  - @Version enables optimistic locking; critical for concurrent stock deductions from
 *    multiple order-service instances (ObjectOptimisticLockingFailureException → 409)
 *  - stockQuantity: units currently available for purchase
 *  - reservedQuantity: informational — units held by pending orders
 *  - lowStockThreshold: STOCK_LOW Kafka event fires when stockQuantity drops below this
 *  - originalPrice null → no discount; originalPrice > price → item is on sale
 *  - updatedAt managed by @PrePersist/@PreUpdate (mirrors MySQL ON UPDATE CURRENT_TIMESTAMP)
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Previous / full price — if set and > price, the item is on sale. */
    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    /** Informational — units reserved by PENDING orders; not enforced by this service. */
    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    /** When stockQuantity drops below this threshold, a STOCK_LOW Kafka event is published. */
    @Column(name = "low_stock_threshold", nullable = false)
    @Builder.Default
    private Integer lowStockThreshold = 10;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** Product weight in kg — used for shipping calculation. */
    @Column(precision = 8, scale = 3)
    private BigDecimal weight;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null)    status = ProductStatus.ACTIVE;
        if (stockQuantity == null)    stockQuantity = 0;
        if (reservedQuantity == null) reservedQuantity = 0;
        if (lowStockThreshold == null) lowStockThreshold = 10;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
