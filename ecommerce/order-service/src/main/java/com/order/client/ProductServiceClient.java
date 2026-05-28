package com.order.client;

import com.order.client.dto.ProductDto;
import com.order.client.dto.StockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for product-service.
 *
 * getProduct()    — validates the product exists, snapshots name/sku/price at order time.
 * deductStock()   — reduces available stock when an order is placed.
 * restoreStock()  — returns stock when an order is cancelled (if stock was already deducted).
 */
@FeignClient(name = "product-service", url = "${services.product-service.url}")
public interface ProductServiceClient {

    @GetMapping("/api/v1/products/{id}")
    ProductDto getProduct(@PathVariable("id") Long id);

    @PostMapping("/api/v1/products/{id}/deduct-stock")
    void deductStock(@PathVariable("id") Long id, @RequestBody StockRequest request);

    @PostMapping("/api/v1/products/{id}/restore-stock")
    void restoreStock(@PathVariable("id") Long id, @RequestBody StockRequest request);
}
