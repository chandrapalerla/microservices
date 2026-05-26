package com.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * Central gateway routing configuration.
 *
 * WHY direct URLs instead of lb://SERVICE:
 *   HandlerFunctions.http() creates its own internal RestClient that is NOT
 *   configured with Spring Cloud LoadBalancer.  When a lb:// URI reaches
 *   Apache HttpClient 5 unresolved it throws:
 *     "Unroutable protocol scheme: lb://USER-SERVICE"
 *
 *   Using a direct URL eliminates the dependency on Eureka being up for routing.
 *   - Local dev  → http://localhost:2026  (default, works without Eureka)
 *   - Kubernetes → http://user-service.default.svc.cluster.local:2026
 *   Override per environment via:
 *     GATEWAY_ROUTES_USER-SERVICE=http://...  (env var)
 *     --gateway.routes.user-service=http://... (CLI arg)
 *
 * HOW TO ADD A NEW MICROSERVICE:
 * 1. Add a property  gateway.routes.my-service: http://localhost:PORT
 * 2. Create a @Bean  myServiceRoutes() — copy the userServiceRoutes pattern.
 * 3. Add its paths to SecurityConfig.securityFilterChain() authorizeHttpRequests block.
 */
@Configuration
public class GatewayConfig {

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    /** Resolved to http://localhost:2026 locally; override per environment. */
    @Value("${gateway.routes.user-service:http://localhost:2026}")
    private String userServiceUrl;

    // ─────────────────────────────────────────────────────────────
    // USER SERVICE  (default: localhost:2026)
    // ─────────────────────────────────────────────────────────────
    @Bean
    public RouterFunction<ServerResponse> userServiceRoutes() {
        return GatewayRouterFunctions.route("user-service-users")
                .route(path("/api/v1/users/**"),
                        HandlerFunctions.http(userServiceUrl))
                .filter(FilterFunctions.addRequestHeader("X-Gateway-Secret", gatewaySecret))
                .build()
                .and(
                    GatewayRouterFunctions.route("user-service-auth")
                        .route(path("/api/v1/auth/**"),
                                HandlerFunctions.http(userServiceUrl))
                        .filter(FilterFunctions.addRequestHeader("X-Gateway-Secret", gatewaySecret))
                        .build()
                );
    }

    // ─────────────────────────────────────────────────────────────
    // FUTURE: ORDER SERVICE
    // ─────────────────────────────────────────────────────────────
    // @Value("${gateway.routes.order-service:http://localhost:2028}")
    // private String orderServiceUrl;
    //
    // @Bean
    // public RouterFunction<ServerResponse> orderServiceRoutes() {
    //     return GatewayRouterFunctions.route("order-service")
    //             .route(path("/api/v1/orders/**"),
    //                     HandlerFunctions.http(orderServiceUrl))
    //             .filter(FilterFunctions.addRequestHeader("X-Gateway-Secret", gatewaySecret))
    //             .build();
    // }
}
