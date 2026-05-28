package com.user.service;

import com.user.entity.User;
import com.user.enums.UserRole;
import com.user.enums.UserStatus;
import com.user.exception.ResourceNotFoundException;
import com.user.observability.UserMetrics;
import com.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMetrics    userMetrics;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "usersPage", allEntries = true)
    public User create(User user) {
        user.setId(null);
        user.setVersion(null);
        // Defaults set by @PrePersist; allow caller to override role/status
        if (user.getRole()   == null) user.setRole(UserRole.USER);
        if (user.getStatus() == null) user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.saveAndFlush(user);
        userMetrics.incrementCreated();
        return saved;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Cacheable(value = "usersPage", key = "#pageable")
    public Page<User> getAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#id")
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * Full update — replaces all mutable fields.
     * The caller must supply the current `version` for optimistic locking.
     * A mismatch (concurrent edit) throws ObjectOptimisticLockingFailureException → HTTP 409.
     *
     * Note: createdAt is never overwritten (updatable=false on the column).
     */
    @Transactional
    @CachePut(value = "users", key = "#id")
    @CacheEvict(value = "usersPage", allEntries = true)
    public User update(Long id, User user) {
        User existing = getById(id);

        existing.setName(user.getName());
        existing.setEmail(user.getEmail());

        // Optional fields — only override when provided
        if (user.getPhone()  != null) existing.setPhone(user.getPhone());
        if (user.getRole()   != null) existing.setRole(user.getRole());
        if (user.getStatus() != null) existing.setStatus(user.getStatus());

        // @PreUpdate will refresh updatedAt automatically
        User saved = userRepository.saveAndFlush(existing);
        userMetrics.incrementUpdated();
        return saved;
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users",     key = "#id"),
            @CacheEvict(value = "usersPage", allEntries = true)
    })
    public void delete(Long id) {
        // Verify the user exists before attempting delete
        getById(id);
        userRepository.deleteById(id);
        userMetrics.incrementDeleted();
    }
}
