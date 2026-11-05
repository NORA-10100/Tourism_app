package com.example.kakeibo.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.kakeibo.User;

// メモ: 利用者データの検索と保存をデータベースに依頼します。
public interface UserRepository extends JpaRepository<User, Long> {
    // メモ: メールアドレスから利用者を検索します。
    Optional<User> findByEmail(String email);
    // メモ: メールアドレスが登録済みか調べます。
    boolean existsByEmail(String email);

    // メモ: 利用者情報を作ってデータベースへ保存します。
    default void save(String username, String email, String passwordHash) {
        saveAndFlush(new User(username, email, passwordHash));
    }

    // メモ: ログイン時に照合するパスワード情報を取得します。
    default Optional<String> findPasswordHashByEmail(String email) {
        return findByEmail(email).map(user -> user.getPasswordHash());
    }

    // メモ: ログイン後に表示する利用者名を取得します。
    default Optional<String> findUsernameByEmail(String email) {
        return findByEmail(email).map(user -> user.getUsername());
    }
}
