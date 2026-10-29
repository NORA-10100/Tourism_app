package com.example.kakeibo.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.kakeibo.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    default void save(String username, String email, String passwordHash) {
        saveAndFlush(new User(username, email, passwordHash));
    }

    default Optional<String> findPasswordHashByEmail(String email) {
        return findByEmail(email).map(User::getPasswordHash);
    }

    default Optional<String> findUsernameByEmail(String email) {
        return findByEmail(email).map(User::getUsername);
    }
}
