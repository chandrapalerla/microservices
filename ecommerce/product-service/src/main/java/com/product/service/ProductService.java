package com.product.service;

import com.product.dto.request.*;
import com.product.dto.response.ProductResponse;
import com.product.dto.response.ProductSummaryResponse;
import com.product.entity.Category;
import com.product.entity.Product;
import com.product.enums.ProductStatus;
import com.product.exception.DuplicateSkuException;
import com.product.exception.InsufficientStockException;
import com.product.exception.ResourceNotFoundException;
import com.product.kafka.ProductEventPublisher;
import com.product.mapper.ProductMapper;
import com.product.observability.ProductMetrics;
import com.product.repository.ProductRepository;
import com.product.specification.ProductSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Core business logic for Product management.
 *
 * Cache strategy:
 *   "products"     — individual product by ID; TTL 10 min
 *   "productsPage" — paginated/search results; TTL 5 min
 *
 * All write operations evict both caches to maintain consistency.
 *
 * Stock operations use @Version optimistic locking.  Concurrent modifications
 * throw ObjectOptimisticLockingFailureException → handled as HTTP 409.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository     productRepository;
    private final CategoryService       categoryService;
    private final ProductMapper         productMapper;
    private final ProductEventPublisher eventPublisher;
    private final ProductMetrics        productMetrics;

    // ── Read operations ───────────────────────────────────────────────────────

    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return productMapper.toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getAll(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(productMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getActive(Pageable pageable) {
        return productRepository.findByStatus(ProductStatus.ACTIVE, pageable)
                .map(productMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getByCategory(Long categoryId, Pageable pageable) {
        // Verify category exists
        categoryService.findById(categoryId);
        return productRepository.findByCategoryIdAndStatus(categoryId, ProductStatus.ACTIVE, pageable)
                .map(productMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getLowStock(Pageable pageable) {
        return productRepository.findLowStock(ProductStatus.ACTIVE, pageable)
                .map(productMapper::toSummary);
    }

    /**
     * Dynamic search using JPA Criteria API.
     * Results are cached by the serialized filter key.
     */
    @Cacheable(value = "productsPage", key = "#filter.cacheKey() + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> search(ProductSearchRequest filter, Pageable pageable) {
        return productRepository.findAll(ProductSpecification.withFilters(filter), pageable)
                .map(productMapper::toSummary);
    }

    // ── Write operations ──────────────────────────────────────────────────────

    @Caching(evict = {
            @CacheEvict(value = "productsPage", allEntries = true)
    })
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }

        Category category = categoryService.findById(request.getCategoryId());

        Product product = Product.builder()
                .name(request.getName())
                .sku(request.getSku())
                .description(request.getDescription())
                .brand(request.getBrand())
                .category(category)
                .price(request.getPrice())
                .originalPrice(request.getOriginalPrice())
                .stockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0)
                .lowStockThreshold(request.getLowStockThreshold() != null ? request.getLowStockThreshold() : 10)
                .thumbnailUrl(request.getThumbnailUrl())
                .weight(request.getWeight())
                .build();

        Product saved = productRepository.save(product);
        log.info("Created product id={} sku={}", saved.getId(), saved.getSku());
        eventPublisher.publishProductCreated(saved);
        productMetrics.incrementProductCreated();
        return productMapper.toResponse(saved);
    }

    @Caching(put = {
            @CachePut(value = "products", key = "#id")
    }, evict = {
            @CacheEvict(value = "productsPage", allEntries = true)
    })
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findById(id);

        // SKU uniqueness check — allow keeping the same SKU
        if (productRepository.existsBySkuAndIdNot(request.getSku(), id)) {
            throw new DuplicateSkuException(request.getSku());
        }

        Category category = categoryService.findById(request.getCategoryId());

        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setDescription(request.getDescription());
        product.setBrand(request.getBrand());
        product.setCategory(category);
        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        if (request.getLowStockThreshold() != null) {
            product.setLowStockThreshold(request.getLowStockThreshold());
        }
        if (request.getThumbnailUrl() != null) {
            product.setThumbnailUrl(request.getThumbnailUrl());
        }
        if (request.getWeight() != null) {
            product.setWeight(request.getWeight());
        }

        Product saved = productRepository.save(product);
        log.info("Updated product id={}", saved.getId());
        eventPublisher.publishProductUpdated(saved);
        return productMapper.toResponse(saved);
    }

    @Caching(put = {
            @CachePut(value = "products", key = "#id")
    }, evict = {
            @CacheEvict(value = "productsPage", allEntries = true)
    })
    @Transactional
    public ProductResponse updatePrice(Long id, UpdatePriceRequest request) {
        Product product = findById(id);

        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());

        Product saved = productRepository.save(product);
        log.info("Updated price for product id={} new price={}", id, saved.getPrice());
        eventPublisher.publishProductUpdated(saved);
        return productMapper.toResponse(saved);
    }

    @Caching(put = {
            @CachePut(value = "products", key = "#id")
    }, evict = {
            @CacheEvict(value = "productsPage", allEntries = true)
    })
    @Transactional
    public ProductResponse updateStatus(Long id, UpdateStatusRequest request) {
        Product product = findById(id);
        ProductStatus oldStatus = product.getStatus();

        product.setStatus(request.getStatus());

        Product saved = productRepository.save(product);
        log.info("Changed status of product id={} from {} to {}", id, oldStatus, request.getStatus());
        eventPublisher.publishStatusChanged(saved);
        return productMapper.toResponse(saved);
    }

    /**
     * Soft-delete: sets status to DISCONTINUED, never hard-deletes.
     */
    @Caching(evict = {
            @CacheEvict(value = "products", key = "#id"),
            @CacheEvict(value = "productsPage", allEntries = true)
    })
    @Transactional
    public void delete(Long id) {
        Product product = findById(id);
        product.setStatus(ProductStatus.DISCONTINUED);
        productRepository.save(product);
        log.info("Soft-deleted (DISCONTINUED) product id={}", id);
        eventPublisher.publishStatusChanged(product);
    }

    // ── Stock operations ──────────────────────────────────────────────────────

    /**
     * Deducts stock when an order is placed.
     * Uses @Version optimistic locking — concurrent deductions are safe.
     *
     * @throws InsufficientStockException if available stock < requested quantity
     */
    @Caching(put = {
            @CachePut(value = "products", key = "#id")
    }, evict = {
            @CacheEvict(value = "productsPage", allEntries = true)
    })
    @Transactional
    public ProductResponse deductStock(Long id, UpdateStockRequest request) {
        Product product = findById(id);
        int requested  = request.getQuantity();
        int available  = product.getStockQuantity() != null ? product.getStockQuantity() : 0;

        if (available < requested) {
            throw new InsufficientStockException(id, requested, available);
        }

        product.setStockQuantity(available - requested);

        // Auto-set status to OUT_OF_STOCK when stock hits zero
        if (product.getStockQuantity() == 0 && product.getStatus() == ProductStatus.ACTIVE) {
            product.setStatus(ProductStatus.OUT_OF_STOCK);
        }

        Product saved = productRepository.save(product);
        log.info("Deducted {} units from product id={} remaining={}", requested, id, saved.getStockQuantity());
        eventPublisher.publishStockDeducted(saved, requested);
        productMetrics.incrementStockDeducted();
        if (saved.getStockQuantity() == 0) productMetrics.incrementOutOfStock();
        return productMapper.toResponse(saved);
    }

    /**
     * Restores stock when an order is cancelled or returned.
     */
    @Caching(put = {
            @CachePut(value = "products", key = "#id")
    }, evict = {
            @CacheEvict(value = "productsPage", allEntries = true)
    })
    @Transactional
    public ProductResponse restoreStock(Long id, UpdateStockRequest request) {
        Product product = findById(id);
        int current   = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        int restored  = request.getQuantity();

        product.setStockQuantity(current + restored);

        // Re-activate if product was OUT_OF_STOCK due to zero stock
        if (product.getStatus() == ProductStatus.OUT_OF_STOCK && product.getStockQuantity() > 0) {
            product.setStatus(ProductStatus.ACTIVE);
        }

        Product saved = productRepository.save(product);
        log.info("Restored {} units for product id={} new total={}", restored, id, saved.getStockQuantity());
        eventPublisher.publishStockRestored(saved, restored);
        productMetrics.incrementStockRestored();
        return productMapper.toResponse(saved);
    }

    /**
     * Admin sets absolute stock value directly.
     */
    @Caching(put = {
            @CachePut(value = "products", key = "#id")
    }, evict = {
            @CacheEvict(value = "productsPage", allEntries = true)
    })
    @Transactional
    public ProductResponse updateStock(Long id, UpdateStockRequest request) {
        Product product = findById(id);
        int oldQty = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        int newQty = request.getQuantity();
        int delta  = newQty - oldQty;

        product.setStockQuantity(newQty);

        // Sync status with new stock level
        if (newQty == 0 && product.getStatus() == ProductStatus.ACTIVE) {
            product.setStatus(ProductStatus.OUT_OF_STOCK);
        } else if (newQty > 0 && product.getStatus() == ProductStatus.OUT_OF_STOCK) {
            product.setStatus(ProductStatus.ACTIVE);
        }

        Product saved = productRepository.save(product);
        log.info("Admin set stock for product id={} old={} new={}", id, oldQty, newQty);
        eventPublisher.publishStockUpdated(saved, delta);
        return productMapper.toResponse(saved);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }
}
