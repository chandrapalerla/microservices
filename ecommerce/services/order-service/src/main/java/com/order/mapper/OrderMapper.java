package com.order.mapper;

import com.order.dto.response.OrderItemResponse;
import com.order.dto.response.OrderResponse;
import com.order.dto.response.OrderStatusHistoryResponse;
import com.order.entity.Order;
import com.order.entity.OrderItem;
import com.order.entity.OrderStatusHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;

/**
 * MapStruct compile-time mapper: Order entity ↔ OrderResponse DTO.
 *
 * Flat shipping-address columns on Order are assembled into the nested
 * ShippingAddressResponse via the mapShippingAddress() default method.
 * estimatedDelivery is computed as createdAt + 7 days.
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "shippingAddress", expression = "java(mapShippingAddress(order))")
    @Mapping(target = "estimatedDelivery", expression = "java(estimatedDelivery(order))")
    OrderResponse toResponse(Order order);

    OrderItemResponse toItemResponse(OrderItem item);

    OrderStatusHistoryResponse toHistoryResponse(OrderStatusHistory history);

    // ── Helper methods (called by the expressions above) ──────────────────────

    default OrderResponse.ShippingAddressResponse mapShippingAddress(Order order) {
        if (order == null) return null;
        return OrderResponse.ShippingAddressResponse.builder()
                .fullName(order.getShippingFullName())
                .phone(order.getShippingPhone())
                .street(order.getShippingStreet())
                .city(order.getShippingCity())
                .state(order.getShippingState())
                .zip(order.getShippingZip())
                .country(order.getShippingCountry())
                .build();
    }

    default LocalDate estimatedDelivery(Order order) {
        if (order == null || order.getCreatedAt() == null) return null;
        // Delivered orders have no future estimate; undelivered: +7 days from creation
        if (order.getDeliveredAt() != null) return order.getDeliveredAt().toLocalDate();
        return order.getCreatedAt().toLocalDate().plusDays(7);
    }
}
