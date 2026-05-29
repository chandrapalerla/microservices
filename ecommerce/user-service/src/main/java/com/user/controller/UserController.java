package com.user.controller;

import com.user.dto.UserDto;
import com.user.entity.User;
import com.user.enums.UserStatus;
import com.user.exception.ResourceNotFoundException;
import com.user.mapper.UserMapper;
import com.user.repository.UserRepository;
import com.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for managing users stored in MySQL with caching and optimistic locking")
public class UserController {

    private final UserService    userService;
    private final UserMapper     userMapper;
    private final UserRepository userRepository;

    // ── Current user profile ─────────────────────────────────────────────────

    @Operation(summary = "Get the current user's own profile",
               description = "Looks up the DB user record using the email claim from the JWT. " +
                             "Accessible to any authenticated user (USER or ADMIN).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile returned",
                     content = @Content(schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "404", description = "No DB user matches the JWT email")
    })
    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            // Fall back to preferred_username as email (some Keycloak configs omit email claim)
            email = jwt.getClaimAsString("preferred_username");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No user record found for the authenticated account. " +
                        "Please ask an admin to create your profile."));
        return ResponseEntity.ok(userMapper.toDto(user));
    }

    @Operation(summary = "Create a new user", description = "Creates a new user. ID and version should be null on request.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created successfully",
                     content = @Content(schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body (validation failed)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody UserDto userDto) {
        User toCreate = userMapper.toEntity(userDto);
        toCreate.setId(null);
        toCreate.setVersion(null);
        User created = userService.create(toCreate);
        return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toDto(created));
    }

    /**
     * GET /api/v1/users?page=0&size=10&sort=name,asc
     *
     * Spring MVC auto-resolves {@link Pageable} from the query string via
     * {@code PageableHandlerMethodArgumentResolver} (registered by Spring Data Web
     * auto-configuration — no manual setup needed in servlet mode).
     */
    @Operation(summary = "Get all users with pagination",
               description = "Retrieves a paginated list of users. Query params: page, size, sort.")
    @ApiResponse(responseCode = "200", description = "Paginated list of users",
                 content = @Content(schema = @Schema(implementation = Page.class)))
    @GetMapping
    public Page<UserDto> getAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.getAll(pageable).map(userMapper::toDto);
    }

    @Operation(summary = "Get user by ID",
               description = "Retrieves a specific user by ID. Result is cached for 10 minutes.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found",
                     content = @Content(schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userMapper.toDto(userService.getById(id)));
    }

    @Operation(summary = "Update an existing user",
               description = "Updates a user. Include the current version for optimistic locking. Returns 409 on version mismatch.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User updated successfully",
                     content = @Content(schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "409", description = "Conflict — optimistic lock version mismatch")
    })
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> update(@PathVariable Long id,
                                          @Valid @RequestBody UserDto userDto) {
        User toUpdate = userMapper.toEntity(userDto);
        User updated = userService.update(id, toUpdate);
        return ResponseEntity.ok(userMapper.toDto(updated));
    }

    @Operation(summary = "Change a user's status",
               description = "ADMIN only. Sets the account status to ACTIVE, INACTIVE, or SUSPENDED " +
                             "without requiring the full user payload. Publishes USER_DEACTIVATED for " +
                             "INACTIVE/SUSPENDED transitions.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated",
                     content = @Content(schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "403", description = "Caller is not ADMIN"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> updateStatus(@PathVariable Long id,
                                                @RequestParam UserStatus status) {
        return ResponseEntity.ok(userMapper.toDto(userService.updateStatus(id, status)));
    }

    @Operation(summary = "Revoke all active sessions for a user",
               description = "ADMIN only. Calls the Keycloak Admin API to log out the user from all " +
                             "devices immediately. Requires the 'manage-users' service-account role on " +
                             "'user-service-client' in Keycloak realm-management.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Sessions revoked"),
        @ApiResponse(responseCode = "403", description = "Caller is not ADMIN"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Keycloak Admin API unreachable or misconfigured")
    })
    @PostMapping("/{id}/revoke-sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revokeSessions(@PathVariable Long id) {
        userService.revokeUserSessions(id);
        return ResponseEntity.noContent().build();
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
