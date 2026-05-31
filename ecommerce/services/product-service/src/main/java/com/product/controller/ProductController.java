package com.product.controller;

import com.product.dto.request.*;
import com.product.dto.response.ProductResponse;
import com.product.dto.response.ProductSummaryResponse;
import com.product.service.ProductService;
import com.product.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST endpoints for Product management.
 *
 * Public GET endpoints (no auth, enforced at SecurityConfig level):
 *   GET  /api/v1/products                         — list all active products (paged)
 *   GET  /api/v1/products/{id}                    — get product by ID
 *   GET  /api/v1/products/search                  — dynamic search with filters
 *   GET  /api/v1/products/category/{categoryId}   — list by category
 *
 * Authenticated (USER or ADMIN — called by order-service via gateway):
 *   POST /api/v1/products/{id}/deduct-stock       — deduct stock for order
 *   POST /api/v1/products/{id}/restore-stock      — restore stock on cancellation
 *
 * Admin only:
 *   GET    /api/v1/products/all                   — all products regardless of status
 *   GET    /api/v1/products/low-stock             — ACTIVE products below threshold
 *   POST   /api/v1/products                       — create product
 *   PUT    /api/v1/products/{id}                  — full update
 *   PATCH  /api/v1/products/{id}/price            — price-only update
 *   PATCH  /api/v1/products/{id}/status           — status change
 *   PATCH  /api/v1/products/{id}/stock            — absolute stock set (admin)
 *   DELETE /api/v1/products/{id}                  — soft-delete (→ DISCONTINUED)
 */
@Tag(name = "Products", description = "Product catalogue — listing, search, stock, and admin management")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final S3Service      s3Service;

    // ── Public read endpoints ─────────────────────────────────────────────────

    @Operation(summary = "List all ACTIVE products (paged)")
    @GetMapping
    public ResponseEntity<Page<ProductSummaryResponse>> getActive(
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(productService.getActive(pageable));
    }

    @Operation(summary = "Get a single product by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @Operation(summary = "Search products with dynamic filters",
               description = "Filter by name, brand, categoryId, price range, inStockOnly, status. All fields optional.")
    @GetMapping("/search")
    public ResponseEntity<Page<ProductSummaryResponse>> search(
            @ParameterObject ProductSearchRequest filter,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.search(filter, pageable));
    }

    @Operation(summary = "Full-text search across name, description, and brand",
               description = "Uses MySQL FULLTEXT index (BOOLEAN MODE). Supports +required, -excluded, \"phrase\" operators. " +
                             "Returns unranked list of matching ACTIVE products. " +
                             "Example: q=laptop +gaming -refurbished")
    @GetMapping("/fulltext-search")
    public ResponseEntity<List<ProductSummaryResponse>> fullTextSearch(@RequestParam String q) {
        return ResponseEntity.ok(productService.fullTextSearch(q));
    }

    @Operation(summary = "List ACTIVE products in a specific category (paged)")
    public ResponseEntity<Page<ProductSummaryResponse>> getByCategory(
            @PathVariable Long categoryId,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(productService.getByCategory(categoryId, pageable));
    }

    // ── Stock operations (order-service calls these via Feign) ────────────────

    @Operation(summary = "Deduct stock for an order (order-service / admin)")
    @PostMapping("/{id}/deduct-stock")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ProductResponse> deductStock(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockRequest request) {
        return ResponseEntity.ok(productService.deductStock(id, request));
    }

    @Operation(summary = "Restore stock on order cancellation (order-service / admin)")
    @PostMapping("/{id}/restore-stock")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ProductResponse> restoreStock(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockRequest request) {
        return ResponseEntity.ok(productService.restoreStock(id, request));
    }

    // ── Admin-only endpoints ──────────────────────────────────────────────────

    @Operation(summary = "List ALL products regardless of status (admin)")
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ProductSummaryResponse>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(productService.getAll(pageable));
    }

    @Operation(summary = "List ACTIVE products where stock < lowStockThreshold (admin)")
    @GetMapping("/low-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ProductSummaryResponse>> getLowStock(
            @ParameterObject @PageableDefault(size = 20, sort = "stockQuantity") Pageable pageable) {
        return ResponseEntity.ok(productService.getLowStock(pageable));
    }

    @Operation(summary = "Create a new product")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.create(request));
    }

    @Operation(summary = "Full update of a product")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @Operation(summary = "Update only price / originalPrice")
    @PatchMapping("/{id}/price")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updatePrice(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePriceRequest request) {
        return ResponseEntity.ok(productService.updatePrice(id, request));
    }

    @Operation(summary = "Change product status (ACTIVE / INACTIVE / DISCONTINUED)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(productService.updateStatus(id, request));
    }

    @Operation(summary = "Admin: set absolute stock quantity")
    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockRequest request) {
        return ResponseEntity.ok(productService.updateStock(id, request));
    }

    @Operation(summary = "Soft-delete product (sets status to DISCONTINUED)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Upload a product thumbnail image (ADMIN only)",
               description = "Accepts JPEG, PNG, WebP, or GIF; max 5 MB. " +
                             "Stores in S3 / MinIO and updates the product's thumbnailUrl. " +
                             "Returns the updated product with the new public image URL.")
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> uploadImage(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        String key = "products/" + id + "/" + sanitizeFilename(file.getOriginalFilename());
        String url = s3Service.upload(key, file);
        return ResponseEntity.ok(productService.updateThumbnail(id, url));
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "image";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
