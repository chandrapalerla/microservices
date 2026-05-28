package com.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** DTO for one status-history entry in the order audit trail. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistoryResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String fromStatus;
    private String toStatus;
    private String reason;
    private String changedBy;
    private LocalDateTime changedAt;
}
