package com.example.kakeibo.language;

import java.util.Locale;

import org.springframework.web.servlet.LocaleResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * セッションに保存した言語コード（ja / en / zh-CN / zh-TW / ko）を、
 * Spring と Thymeleaf が使う Locale に変換します。
 */
public class SessionLanguageLocaleResolver implements LocaleResolver {

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        // メモ: セッションを新規作成せず既存の言語設定だけを参照します。
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object value = session.getAttribute("language");
            if (value instanceof String code && LanguageSupport.isSupported(code)) {
                return LanguageSupport.toLocale(code);
            }
        }
        // 言語をまだ選んでいないときは日本語で表示します。
        // 端末の言語は言語選択画面で「おすすめ」として表示します。
        return Locale.JAPANESE;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        if (locale != null) {
            // メモ: Springから言語変更された場合も、次のリクエスト向けにセッションへ保存します。
            request.getSession(true).setAttribute("language",
                    LanguageSupport.fromLocale(locale));
        }
    }
}
