package com.user.controller;

import com.user.mysql.entity.User;
import com.user.service.UserService;
import com.user.dto.UserDto;
import com.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for managing users stored in MySQL with caching and optimistic locking")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @Operation(summary = "Create a new user", description = "Creates a new user. ID and version should be null on request.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created successfully", content = @Content(schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body (validation failed)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody UserDto userDto) {
        User toCreate = userMapper.toEntity(userDto);
        toCreate.setId(null);
        toCreate.setVersion(null);
        User created = userService.create(toCreate);
        UserDto dto = userMapper.toDto(created);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Get all users with pagination", description = "Retrieves a paginated list of all users. Supports pagination and sorting.")
    @ApiResponse(responseCode = "200", description = "List of users", content = @Content(schema = @Schema(implementation = Page.class)))
    @GetMapping
    public Page<UserDto> getAll(@PageableDefault(size = 20) Pageable pageable) {
        return userService.getAll(pageable).map(userMapper::toDto);
    }

    @Operation(summary = "Get user by ID", description = "Retrieves a specific user by ID. Result is cached for 10 minutes.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found", content = @Content(schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        return ResponseEntity.ok(userMapper.toDto(user));
    }

    @Operation(summary = "Update an existing user", description = "Updates a user. Include the current version for optimistic locking. Returns 409 if version mismatch.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User updated successfully", content = @Content(schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "409", description = "Conflict - optimistic lock version mismatch")
    })
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> update(@PathVariable Long id, @Valid @RequestBody UserDto userDto) {
        User toUpdate = userMapper.toEntity(userDto);
        User updated = userService.update(id, toUpdate);
        return ResponseEntity.ok(userMapper.toDto(updated));
    }

    @Operation(summary = "Delete a user", description = "Deletes a user by ID. Pagination cache is invalidated.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User deleted successfully"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {

        userService.delete(id);

        return ResponseEntity.noContent().build();
    }
}