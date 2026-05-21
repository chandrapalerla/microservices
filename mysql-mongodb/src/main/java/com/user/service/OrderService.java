package com.user.service;

import com.user.mongodb.document.Order;
import com.user.mongodb.repository.OrderRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

	private final OrderRepository orderRepository;

    @Transactional
    @CacheEvict(value = "ordersPage", allEntries = true)
    public Order create(Order order) {
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "ordersPage", key = "T(java.util.Objects).hash(#pageable.pageNumber, #pageable.pageSize, #pageable.sort)")
    public Page<Order> getAll(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    // Backwards-compatible convenience method
    @Transactional(readOnly = true)
    @Cacheable(value = "orders", key = "'all'")
    public List<Order> getAll() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "orders", key = "#id")
    public Order getById(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new com.user.exception.ResourceNotFoundException("Order not found"));
    }

    @Transactional
    @CachePut(value = "orders", key = "#id")
    @CacheEvict(value = "ordersPage", allEntries = true)
    public Order update(String id, Order order) {

        Order existing = getById(id);

        existing.setProductName(order.getProductName());
        existing.setPrice(order.getPrice());

        return orderRepository.save(existing);
    }

    @Transactional
    @CacheEvict(value = "orders", key = "#id")
    public void delete(String id) {
        orderRepository.deleteById(id);
    }
}