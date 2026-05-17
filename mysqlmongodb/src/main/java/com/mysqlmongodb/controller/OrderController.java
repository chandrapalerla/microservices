package com.mysqlmongodb.controller;

import com.mysqlmongodb.mongodb.document.Order;
import com.mysqlmongodb.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Order create(@RequestBody Order order) {
        return orderService.create(order);
    }

    @GetMapping
    public List<Order> getAll() {
        return orderService.getAll();
    }

    @GetMapping("/{id}")
    public Order getById(@PathVariable String id) {
        return orderService.getById(id);
    }

    @PutMapping("/{id}")
    public Order update(@PathVariable String id,
                        @RequestBody Order order) {
        return orderService.update(id, order);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable String id) {

        orderService.delete(id);

        return "Order deleted successfully";
    }
}