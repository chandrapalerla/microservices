package com.order.repository;

import com.order.entity.PendingStockRestore;
import com.order.enums.CompensationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PendingStockRestoreRepository extends JpaRepository<PendingStockRestore, Long> {

    List<PendingStockRestore> findByStatusOrderByCreatedAtAsc(CompensationStatus status);
}
