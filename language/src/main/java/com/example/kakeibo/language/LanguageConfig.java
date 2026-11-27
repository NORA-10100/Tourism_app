package com.example.kakeibo.language;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;

/**
 * 多言語対応の設定です。Bean 名を localeResolver にして、
 * Spring Boot の画面表示がこの解決処理を使うようにします。
 */
@Configuration
public class LanguageConfig {

    @Bean
    public LocaleResolver localeResolver() {
        // メモ: ログイン前後で共通のセッション設定から画面言語を解決します。
        return new SessionLanguageLocaleResolver();
    }
}
