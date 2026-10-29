package com.example.kakeibo.service;

import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.kakeibo.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public String register(
            String username,
            String email,
            String password,
            String confirmPassword) {

        if (username == null
                || username.trim().isEmpty()
                || username.trim().length() > 15) {
            return "ユーザー名は1～15文字で入力してください。";
        }

        if (email == null
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return "メールアドレスの形式を確認してください。";
        }

        if (password == null
                || password.length() < 8
                || password.length() > 16
                || !password.matches(
                    "(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).*")) {
            return "パスワードは8～16文字で、大文字・小文字・数字を含めてください。";
        }

        if (!password.equals(confirmPassword)) {
            return "確認用パスワードが一致しません。";
        }

        email = email.trim();

        if (email.length() > 255) {
            return "メールアドレスは255文字以内で入力してください。";
        }

        if (userRepository.existsByEmail(email)) {
            return "このメールアドレスはすでに登録されています。";
        }

        String passwordHash = passwordEncoder.encode(password);

        userRepository.save(
            username.trim(),
            email.trim(),
            passwordHash
        );

        return null;
    }

    public Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    public Optional<String> login(String email, String password) {
        if (email == null || password == null) {
            return Optional.empty();
        }

        Optional<String> storedHash =
                userRepository.findPasswordHashByEmail(email.trim());

        if (storedHash.isEmpty()
                || !passwordEncoder.matches(
                    password, storedHash.get())) {
            return Optional.empty();
        }

        return userRepository.findUsernameByEmail(email.trim());
    }
}