package com.user.service;

import com.user.mysql.entity.User;
import com.user.mysql.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    @CacheEvict(value = "usersPage", allEntries = true)
    public User create(User user) {
        // Ensure ID is null for new users to avoid detached entity issues
        user.setId(null);
        user.setVersion(null);
        return userRepository.saveAndFlush(user);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "'all'")
    public List<User> getAll() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "usersPage", key = "T(java.util.Objects).hash(#pageable.pageNumber, #pageable.pageSize, #pageable.sort)")
    public Page<User> getAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#id")
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new com.user.exception.ResourceNotFoundException("User not found"));
    }

    @Transactional
    @CachePut(value = "users", key = "#id")
    @CacheEvict(value = "usersPage", allEntries = true)
    public User update(Long id, User user) {

        User existing = getById(id);

        existing.setName(user.getName());
        existing.setEmail(user.getEmail());

        return userRepository.saveAndFlush(existing);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#id")
    public void delete(Long id) {
        userRepository.deleteById(id);
    }
}