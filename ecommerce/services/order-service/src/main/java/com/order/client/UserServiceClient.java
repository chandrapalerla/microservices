package com.order.client;

import com.order.client.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for user-service.
 *
 * url property overrides Eureka-based resolution — uses a direct HTTP URL so
 * the lb:// scheme is avoided (HandlerFunctions.http() is not load-balancer-aware).
 * FeignSecurityConfig.jwtRelayInterceptor() adds:
 *   Authorization: Bearer <jwt>  (so user-service can re-validate the token)
 *   X-Gateway-Secret             (so user-service accepts the request)
 */
@FeignClient(name = "user-service", url = "${services.user-service.url}")
public interface UserServiceClient {

    @GetMapping("/api/v1/users/{id}")
    UserDto getUser(@PathVariable("id") Long id);
}
