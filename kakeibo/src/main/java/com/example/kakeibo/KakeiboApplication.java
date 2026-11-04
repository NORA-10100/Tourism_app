package com.example.kakeibo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// メモ: 独自のログイン処理を使うため、Springの仮ユーザー設定は読み込みません。
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
// メモ: このクラスから家計簿アプリを起動します。
public class KakeiboApplication {

	// メモ: Spring Boot を起動するための最初の処理です。
	public static void main(String[] args) {
		SpringApplication.run(KakeiboApplication.class, args);
	}

}
