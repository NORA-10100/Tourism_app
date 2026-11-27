package com.example.kakeibo.common;

import com.example.kakeibo.language.LanguageSupport;

import jakarta.servlet.http.HttpSession;

/**
 * ログイン状態や表示言語など、セッションに保存している値を取り出す共通処理です。
 */
public final class SessionUser {

    private SessionUser() {
    }

    // メモ: セッションが未作成の場合も安全にログイン状態を判定します。
    public static boolean isLoggedIn(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute("loggedIn"));
    }

    // メモ: 未対応の言語コードを画面へ流さないよう、対応一覧と照合します。
    public static String languageOrNull(HttpSession session) {
        Object value = session == null ? null : session.getAttribute("language");
        if (value instanceof String code && LanguageSupport.isSupported(code)) {
            return code;
        }
        return null;
    }

    // メモ: 呼び出し側で null 判定を繰り返さないよう、日本語を既定値として返します。
    public static String language(HttpSession session) {
        String language = languageOrNull(session);
        return language == null ? LanguageSupport.DEFAULT_LANGUAGE : language;
    }

    /**
     * 支出・旅行プランで使う利用者番号です。ログイン時に保存する
     * {@code expenseUserId = "user-<users.id>"} から数値部分を取り出します。
     */
    public static Integer userId(HttpSession session) {
        Object value = session == null ? null : session.getAttribute("expenseUserId");
        if (!(value instanceof String text)) {
            return null;
        }
        String digits = text.startsWith("user-") ? text.substring("user-".length()) : text;
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
