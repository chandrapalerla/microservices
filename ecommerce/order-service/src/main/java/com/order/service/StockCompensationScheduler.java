package com.order.service;

import com.order.client.ProductServiceClient;
import com.order.client.dto.StockRequest;
import com.order.entity.PendingStockRestore;
import com.order.enums.CompensationStatus;
import com.order.repository.PendingStockRestoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Saga compensating transaction scheduler.
 *
 * When restoreProductStock()'s circuit breaker opens (product-service down), a
 * PendingStockRestore record is written to the DB instead of silently dropping the
 * restore. This scheduler retries all PENDING records every 30 seconds until
 * product-service recovers, giving up after 5 attempts (marks FAILED for ops).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockCompensationScheduler {

    private static final int MAX_RETRIES = 5;

    private final PendingStockRestoreRepository pendingStockRestoreRepository;
    private final ProductServiceClient          productServiceClient;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void processCompensations() {
        List<PendingStockRestore> pending =
                pendingStockRestoreRepository.findByStatusOrderByCreatedAtAsc(CompensationStatus.PENDING);
        if (pending.isEmpty()) return;

        log.info("StockCompensationScheduler processing {} pending compensation(s)", pending.size());

        for (PendingStockRestore record : pending) {
            try {
                productServiceClient.restoreStock(record.getProductId(), new StockRequest(record.getQuantity()));
                record.setStatus(CompensationStatus.DONE);
                record.setProcessedAt(Instant.now());
                log.info("Stock compensation succeeded for productId={} orderId={}",
                        record.getProductId(), record.getOrderId());
            } catch (Exception e) {
                int retries = record.getRetryCount() + 1;
                record.setRetryCount(retries);
                if (retries >= MAX_RETRIES) {
                    record.setStatus(CompensationStatus.FAILED);
                    log.error("Stock compensation permanently failed for productId={} orderId={} after {} retries: {}",
                            record.getProductId(), record.getOrderId(), retries, e.getMessage());
                } else {
                    log.warn("Stock compensation attempt {} failed for productId={} orderId={}: {}",
                            retries, record.getProductId(), record.getOrderId(), e.getMessage());
                }
            }
        }
    }
}
