package com.payment.repository;

import com.payment.entity.PaymentOutboxEvent;
import com.payment.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxEvent, Long> {

    List<PaymentOutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
