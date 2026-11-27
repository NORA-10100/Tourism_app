package com.example.kakeibo.spot;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.context.MessageSource;

/**
 * 画面に表示するスポット1件分の値です。多言語の名前とカテゴリ名を解決済みにします。
 */
public record SpotView(
        Integer id,
        String name,
        String categoryCode,
        String categoryLabel,
        String area,
        String businessHours,
        String accessInfo,
        Integer crowdScore,
        String crowdLabel,
        BigDecimal reviewScore,
        double latitude,
        double longitude) {

    public static SpotView of(Spot spot, String language, MessageSource messages, Locale locale) {
        // メモ: Thymeleafへ渡す前に名称・カテゴリ・混雑度を表示言語へ解決します。
        String code = categoryCode(spot.getCategory());
        String categoryLabel = messages.getMessage(
                "spot.category." + code, null, spot.getCategory(), locale);
        String crowdLabel = messages.getMessage(
                "spot.crowd." + crowdLevel(spot.getCrowdScore()), null, "", locale);

        return new SpotView(
                spot.getId(),
                LocalizedText.resolve(spot.getName(), language),
                code,
                categoryLabel,
                spot.getArea(),
                spot.getBusinessHours(),
                spot.getAccessInfo(),
                spot.getCrowdScore(),
                crowdLabel,
                spot.getReviewScore(),
                spot.getLatitude() == null ? 0 : spot.getLatitude().doubleValue(),
                spot.getLongitude() == null ? 0 : spot.getLongitude().doubleValue());
    }

    public static String categoryCode(String category) {
        // メモ: DBの日本語カテゴリを翻訳キーの末尾として使えるコードへ変換します。
        if (category == null) {
            return "other";
        }
        return switch (category) {
            case "自然" -> "nature";
            case "グルメ" -> "gourmet";
            case "文化" -> "culture";
            case "体験" -> "experience";
            default -> "other";
        };
    }

    private static String crowdLevel(Integer score) {
        int value = score == null ? 0 : score;
        if (value <= 33) {
            return "low";
        }
        if (value <= 66) {
            return "medium";
        }
        return "high";
    }
}
