package com.mysqlmongodb.mapper;

import com.mysqlmongodb.dto.OrderDto;
import com.mysqlmongodb.mongodb.document.Order;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    OrderDto toDto(Order order);
    Order toEntity(OrderDto dto);
}

