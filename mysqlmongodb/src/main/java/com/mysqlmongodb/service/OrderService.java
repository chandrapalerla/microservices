package com.mysqlmongodb.service;

import com.mysqlmongodb.mongodb.document.Order;
import com.mysqlmongodb.mongodb.repository.OrderRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

	private final OrderRepository orderRepository;

    public Order create(Order order) {
        return orderRepository.save(order);
    }

    public List<Order> getAll() {
        return orderRepository.findAll();
    }

    public Order getById(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    public Order update(String id, Order order) {

        Order existing = getById(id);

        existing.setProductName(order.getProductName());
        existing.setPrice(order.getPrice());

        return orderRepository.save(existing);
    }

    public void delete(String id) {
        orderRepository.deleteById(id);
    }
}