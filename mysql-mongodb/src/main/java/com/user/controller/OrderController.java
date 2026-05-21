package com.user.controller;

import com.user.mongodb.document.Order;
import com.user.service.OrderService;
import com.user.dto.OrderDto;
import com.user.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

@RestController
@RequestMapping({"/orders", "/api/v1/orders"})
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "APIs for managing orders stored in MongoDB with caching and optimistic locking")
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @Operation(summary = "Create a new order", description = "Creates a new order. ID and version should be null on request.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Order created successfully", content = @Content(schema = @Schema(implementation = OrderDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body (validation failed)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<OrderDto> create(@Valid @RequestBody OrderDto orderDto) {
        Order toCreate = orderMapper.toEntity(orderDto);
        toCreate.setId(null);
        toCreate.setVersion(null);
        Order created = orderService.create(toCreate);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderMapper.toDto(created));
    }

    @Operation(summary = "Get all orders with pagination", description = "Retrieves a paginated list of all orders. Supports pagination and sorting.")
    @ApiResponse(responseCode = "200", description = "List of orders", content = @Content(schema = @Schema(implementation = Page.class)))
    @GetMapping
    public Page<OrderDto> getAll(@PageableDefault(size = 20) Pageable pageable) {
        return orderService.getAll(pageable).map(orderMapper::toDto);
    }

    @Operation(summary = "Get order by ID", description = "Retrieves a specific order by ID. Result is cached for 10 minutes.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order found", content = @Content(schema = @Schema(implementation = OrderDto.class))),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getById(@PathVariable String id) {
        Order order = orderService.getById(id);
        return ResponseEntity.ok(orderMapper.toDto(order));
    }

    @Operation(summary = "Update an existing order", description = "Updates an order. Include the current version for optimistic locking. Returns 409 if version mismatch.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order updated successfully", content = @Content(schema = @Schema(implementation = OrderDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "409", description = "Conflict - optimistic lock version mismatch")
    })
    @PutMapping("/{id}")
    public ResponseEntity<OrderDto> update(@PathVariable String id,
                        @Valid @RequestBody OrderDto orderDto) {
        Order toUpdate = orderMapper.toEntity(orderDto);
        Order updated = orderService.update(id, toUpdate);
        return ResponseEntity.ok(orderMapper.toDto(updated));
    }

    @Operation(summary = "Delete an order", description = "Deletes an order by ID. Pagination cache is invalidated.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Order deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {

        orderService.delete(id);

        return ResponseEntity.noContent().build();
    }
}