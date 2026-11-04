package com.example.kakeibo;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
// メモ: 利用者テーブルの1行分を表すクラスです。
public class User {
    // メモ: データベースが自動で付ける利用者番号です。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // メモ: 画面に表示する利用者名です。
    @Column(nullable = false, length = 50)
    private String username;
    // メモ: ログインに使うメールアドレスです。
    @Column(nullable = false, unique = true, length = 255)
    private String email;
    // メモ: 平文ではなく、照合用に変換したパスワードを保存します。
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // メモ: JPA がデータベースから利用者を読み込むときに使います。
    protected User() {}

    // メモ: 新しい利用者を作るときに必要な値を受け取ります。
    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // メモ: 利用者番号を返します。
    public Long getId() { return id; }
    // メモ: 利用者名を返します。
    public String getUsername() { return username; }
    // メモ: 保存済みパスワード情報を返します。
    public String getPasswordHash() { return passwordHash; }
}
