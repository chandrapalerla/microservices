package com.order.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Generic status-change request carrying an optional human-readable reason. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusUpdateRequest {

    private String reason;
}
