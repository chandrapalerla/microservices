package com.order.mapper;

import com.order.dto.response.OrderItemResponse;
import com.order.dto.response.OrderResponse;
import com.order.dto.response.OrderStatusHistoryResponse;
import com.order.entity.Order;
import com.order.entity.OrderItem;
import com.order.entity.OrderStatusHistory;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-28T19:22:49+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 25.0.2 (Oracle Corporation)"
)
@Component
public class OrderMapperImpl implements OrderMapper {

    @Override
    public OrderResponse toResponse(Order order) {
        if ( order == null ) {
            return null;
        }

        OrderResponse.OrderResponseBuilder orderResponse = OrderResponse.builder();

        orderResponse.id( order.getId() );
        orderResponse.orderNumber( order.getOrderNumber() );
        orderResponse.userId( order.getUserId() );
        orderResponse.userEmail( order.getUserEmail() );
        orderResponse.status( order.getStatus() );
        orderResponse.paymentStatus( order.getPaymentStatus() );
        orderResponse.paymentMethod( order.getPaymentMethod() );
        orderResponse.items( orderItemListToOrderItemResponseList( order.getItems() ) );
        orderResponse.subtotal( order.getSubtotal() );
        orderResponse.taxAmount( order.getTaxAmount() );
        orderResponse.shippingAmount( order.getShippingAmount() );
        orderResponse.discountAmount( order.getDiscountAmount() );
        orderResponse.totalAmount( order.getTotalAmount() );
        orderResponse.couponCode( order.getCouponCode() );
        orderResponse.trackingNumber( order.getTrackingNumber() );
        orderResponse.courierName( order.getCourierName() );
        orderResponse.notes( order.getNotes() );
        orderResponse.createdAt( order.getCreatedAt() );
        orderResponse.confirmedAt( order.getConfirmedAt() );
        orderResponse.shippedAt( order.getShippedAt() );
        orderResponse.deliveredAt( order.getDeliveredAt() );
        orderResponse.cancelledAt( order.getCancelledAt() );

        orderResponse.shippingAddress( mapShippingAddress(order) );
        orderResponse.estimatedDelivery( estimatedDelivery(order) );

        return orderResponse.build();
    }

    @Override
    public OrderItemResponse toItemResponse(OrderItem item) {
        if ( item == null ) {
            return null;
        }

        OrderItemResponse.OrderItemResponseBuilder orderItemResponse = OrderItemResponse.builder();

        orderItemResponse.id( item.getId() );
        orderItemResponse.productId( item.getProductId() );
        orderItemResponse.productName( item.getProductName() );
        orderItemResponse.productSku( item.getProductSku() );
        orderItemResponse.quantity( item.getQuantity() );
        orderItemResponse.unitPrice( item.getUnitPrice() );
        orderItemResponse.totalPrice( item.getTotalPrice() );

        return orderItemResponse.build();
    }

    @Override
    public OrderStatusHistoryResponse toHistoryResponse(OrderStatusHistory history) {
        if ( history == null ) {
            return null;
        }

        OrderStatusHistoryResponse.OrderStatusHistoryResponseBuilder orderStatusHistoryResponse = OrderStatusHistoryResponse.builder();

        orderStatusHistoryResponse.fromStatus( history.getFromStatus() );
        orderStatusHistoryResponse.toStatus( history.getToStatus() );
        orderStatusHistoryResponse.reason( history.getReason() );
        orderStatusHistoryResponse.changedBy( history.getChangedBy() );
        orderStatusHistoryResponse.changedAt( history.getChangedAt() );

        return orderStatusHistoryResponse.build();
    }

    protected List<OrderItemResponse> orderItemListToOrderItemResponseList(List<OrderItem> list) {
        if ( list == null ) {
            return null;
        }

        List<OrderItemResponse> list1 = new ArrayList<OrderItemResponse>( list.size() );
        for ( OrderItem orderItem : list ) {
            list1.add( toItemResponse( orderItem ) );
        }

        return list1;
    }
}
