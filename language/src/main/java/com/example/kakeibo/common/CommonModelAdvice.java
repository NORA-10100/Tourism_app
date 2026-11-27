package com.example.kakeibo.common;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.example.kakeibo.language.LanguageSupport;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 全画面の共通レイアウトで使う値を、各コントローラーの Model に追加します。
 */
@ControllerAdvice
public class CommonModelAdvice {

    @ModelAttribute("loggedIn")
    public boolean loggedIn(HttpServletRequest request) {
        // メモ: セッションがない画面表示でも、新しいセッションは作らずにログイン状態を判定します。
        HttpSession session = request.getSession(false);
        return SessionUser.isLoggedIn(session);
    }

    @ModelAttribute("currentLanguage")
    public String currentLanguage(HttpServletRequest request) {
        // メモ: 選択前の画面にも既定言語を渡し、Thymeleaf の lang 属性を常に設定します。
        HttpSession session = request.getSession(false);
        String language = SessionUser.languageOrNull(session);
        return language == null ? LanguageSupport.DEFAULT_LANGUAGE : language;
    }
}
