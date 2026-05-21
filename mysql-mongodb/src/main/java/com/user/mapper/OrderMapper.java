package com.user.mapper;

import com.user.dto.OrderDto;
import com.user.mongodb.document.Order;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    OrderDto toDto(Order order);
    Order toEntity(OrderDto dto);
}

