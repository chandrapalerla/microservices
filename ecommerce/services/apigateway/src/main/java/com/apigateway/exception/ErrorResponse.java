package com.apigateway.exception;

import java.time.Instant;

/**
 * Uniform error envelope returned by the gateway on every non-2xx path.
 *
 * correlationId ties the response to the OTel trace visible in Jaeger/Tempo
 * so support teams can look up the exact span without asking the client for logs.
 */
public record ErrorResponse(
        Instant timestamp,
        int     status,
        String  error,
        String  message,
        String  path,
        String  correlationId
) {}
