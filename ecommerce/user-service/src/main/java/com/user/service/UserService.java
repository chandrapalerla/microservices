package com.user.service;

import com.user.entity.User;
import com.user.enums.UserRole;
import com.user.enums.UserStatus;
import com.user.exception.ResourceNotFoundException;
import com.user.kafka.UserEventPublisher;
import com.user.kafka.UserEventType;
import com.user.observability.UserMetrics;
import com.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
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

    private final UserRepository      userRepository;
    private final UserMetrics         userMetrics;
    private final UserEventPublisher  eventPublisher;
    private final KeycloakAdminClient keycloakAdminClient;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "usersPage", allEntries = true)
    public User create(User user) {
        user.setId(null);
        user.setVersion(null);
        if (user.getRole()   == null) user.setRole(UserRole.USER);
        if (user.getStatus() == null) user.setStatus(UserStatus.ACTIVE);
        user.setName(sanitize(user.getName()));
        user.setPhone(sanitize(user.getPhone()));
        User saved = userRepository.saveAndFlush(user);
        userMetrics.incrementCreated();
        eventPublisher.publish(UserEventType.USER_CREATED, saved);
        return saved;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Cacheable(
        value = "usersPage",
        key = "#pageable.pageNumber + '_' + #pageable.pageSize + '_' + #pageable.sort.toString()"
    )
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
     * Caller must supply the current version for optimistic locking.
     * Publishes USER_ROLE_CHANGED when the role changes, USER_UPDATED otherwise.
     */
    @Transactional
    @CachePut(value = "users", key = "#id")
    @CacheEvict(value = "usersPage", allEntries = true)
    public User update(Long id, User user) {
        User existing = getById(id);
        UserRole oldRole = existing.getRole();

        existing.setName(sanitize(user.getName()));
        existing.setEmail(user.getEmail());
        if (user.getPhone()  != null) existing.setPhone(sanitize(user.getPhone()));
        if (user.getRole()   != null) existing.setRole(user.getRole());
        if (user.getStatus() != null) existing.setStatus(user.getStatus());

        User saved = userRepository.saveAndFlush(existing);
        userMetrics.incrementUpdated();
        eventPublisher.publish(
                oldRole.equals(saved.getRole()) ? UserEventType.USER_UPDATED
                                                : UserEventType.USER_ROLE_CHANGED,
                saved);
        return saved;
    }

    // ── Status ────────────────────────────────────────────────────────────────

    /**
     * Targeted status change — no full payload required.
     * Publishes USER_DEACTIVATED when status becomes INACTIVE or SUSPENDED.
     */
    @Transactional
    @CachePut(value = "users", key = "#id")
    @CacheEvict(value = "usersPage", allEntries = true)
    public User updateStatus(Long id, UserStatus status) {
        User existing = getById(id);
        existing.setStatus(status);
        User saved = userRepository.saveAndFlush(existing);
        userMetrics.incrementUpdated();
        UserEventType eventType = (status == UserStatus.INACTIVE || status == UserStatus.SUSPENDED)
                ? UserEventType.USER_DEACTIVATED
                : UserEventType.USER_UPDATED;
        eventPublisher.publish(eventType, saved);
        return saved;
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users",     key = "#id"),
            @CacheEvict(value = "usersPage", allEntries = true)
    })
    public void delete(Long id) {
        User user = getById(id);          // captured before deletion for the event payload
        userRepository.deleteById(id);
        userMetrics.incrementDeleted();
        eventPublisher.publish(UserEventType.USER_DELETED, user);
    }

    // ── Sessions ──────────────────────────────────────────────────────────────

    public void revokeUserSessions(Long id) {
        User user = getById(id);
        keycloakAdminClient.revokeAllSessions(user.getEmail());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Strips all HTML tags from user-supplied text (Jsoup Safelist.none()).
     * Prevents stored XSS when name or phone values are later rendered in an HTML UI.
     */
    private String sanitize(String input) {
        if (input == null) return null;
        return Jsoup.clean(input.trim(), Safelist.none());
    }
}
