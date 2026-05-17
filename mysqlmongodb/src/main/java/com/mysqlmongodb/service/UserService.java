package com.mysqlmongodb.service;

import com.mysqlmongodb.mysql.entity.User;
import com.mysqlmongodb.mysql.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

    @Transactional
    public User create(User user) {
        // Ensure ID is null for new users to avoid detached entity issues
        user.setId(null);
        user.setVersion(null);
        return userRepository.saveAndFlush(user);
    }

    @Transactional(readOnly = true)
    public List<User> getAll() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public User update(Long id, User user) {

        User existing = getById(id);

        existing.setName(user.getName());
        existing.setEmail(user.getEmail());

        return userRepository.saveAndFlush(existing);
    }

    @Transactional
    public void delete(Long id) {
        userRepository.deleteById(id);
    }
}