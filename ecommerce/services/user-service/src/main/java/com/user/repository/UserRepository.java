package com.user.repository;

import com.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Used by AuthenticationController to find the DB user record from the JWT email claim. */
    java.util.Optional<User> findByEmail(String email);
}