package com.example.kakeibo.service;

import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.kakeibo.repository.UserRepository;

@Service
// メモ: 利用者登録とログイン時の確認をまとめて行います。
public class UserService {

    // メモ: 利用者情報を保存・検索する部品です。
    private final UserRepository userRepository;
    // メモ: パスワードを安全な照合用文字列に変換します。
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    // メモ: Spring から利用者用リポジトリを受け取ります。
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // メモ: 入力を順番に検査し、問題がなければ利用者を保存します。
    @Transactional
    public String register(
            String username,
            String email,
            String password,
            String confirmPassword) {

        // メモ: 名前、メールアドレス、パスワードの条件を一つずつ確認します。
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

        // メモ: 入力されたパスワードは元の文字のまま保存しません。
        String passwordHash = passwordEncoder.encode(password);

        userRepository.save(
            username.trim(),
            email.trim(),
            passwordHash
        );

        return null;
    }

    // メモ: メールアドレスから利用者番号を取得します。
    public Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    // メモ: メールアドレスとパスワードが登録内容と合うか確認します。
    public Optional<String> login(String email, String password) {
        if (email == null || password == null) {
            return Optional.empty();
        }

        // メモ: 保存済みパスワードと入力を照合し、成功したときだけ名前を返します。
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
