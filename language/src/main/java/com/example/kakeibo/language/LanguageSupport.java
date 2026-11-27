package com.example.kakeibo.language;

import java.util.List;
import java.util.Locale;

/**
 * 対応言語の一覧と、画面で使う言語コードと言語設定の相互変換をまとめます。
 */
public final class LanguageSupport {

    public static final String DEFAULT_LANGUAGE = "ja";

    /** 言語選択画面に表示する1行分の情報です。 */
    public record Option(String code, String label, String flag) {
    }

    public static final List<Option> OPTIONS = List.of(
            new Option("ja", "日本語", "🇯🇵"),
            new Option("en", "English", "🇬🇧"),
            new Option("zh-CN", "中文（简体字）", "🇨🇳"),
            new Option("zh-TW", "中文（繁體字）", "🇹🇼"),
            new Option("ko", "한국어", "🇰🇷"));

    private LanguageSupport() {
    }

    public static boolean isSupported(String code) {
        // メモ: 選択肢と保存可能なコードの一覧を一か所で共有します。
        return code != null && OPTIONS.stream().anyMatch(option -> option.code().equals(code));
    }

    public static String normalize(String code) {
        // メモ: 未対応値がLocale変換へ渡った場合も、日本語へ安全に寄せます。
        return isSupported(code) ? code.trim() : DEFAULT_LANGUAGE;
    }

    public static Locale toLocale(String code) {
        // メモ: アプリ内の言語コードを、Springのメッセージ解決に使うLocaleへ変換します。
        return switch (normalize(code)) {
            case "en" -> Locale.ENGLISH;
            case "zh-CN" -> Locale.SIMPLIFIED_CHINESE;
            case "zh-TW" -> Locale.TRADITIONAL_CHINESE;
            case "ko" -> Locale.KOREAN;
            default -> Locale.JAPANESE;
        };
    }

    /** 端末（ブラウザ）の言語設定から、おすすめの表示言語を選びます。 */
    public static String fromLocale(Locale locale) {
        if (locale == null) {
            return DEFAULT_LANGUAGE;
        }
        return switch (locale.getLanguage()) {
            case "en" -> "en";
            case "zh" -> {
                String country = locale.getCountry();
                yield "TW".equals(country) || "HK".equals(country) ? "zh-TW" : "zh-CN";
            }
            case "ko" -> "ko";
            default -> DEFAULT_LANGUAGE;
        };
    }
}
