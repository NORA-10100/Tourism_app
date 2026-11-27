package com.example.kakeibo.spot;

import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * スポット名などに保存している多言語 JSON から、表示する言語の文字列を取り出します。
 * 例: {@code {"ja":"首里城公園","en":"Shuri Castle Park"}}
 */
public final class LocalizedText {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private LocalizedText() {
    }

    public static String resolve(String raw, String language) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) {
            return raw;
        }

        try {
            // メモ: 選択言語を優先し、翻訳がない場合は候補言語や登録済み文字列で補います。
            JsonNode node = MAPPER.readTree(trimmed);
            for (String key : candidates(language)) {
                JsonNode value = node.get(key);
                if (value != null && value.isString() && !value.asString().isBlank()) {
                    return value.asString();
                }
            }
            for (var entry : node.properties()) {
                JsonNode value = entry.getValue();
                if (value.isString() && !value.asString().isBlank()) {
                    return value.asString();
                }
            }
        } catch (JacksonException e) {
            // JSON として読めない場合は、保存されている文字列をそのまま表示します。
            return raw;
        }
        return raw;
    }

    private static List<String> candidates(String language) {
        return switch (language == null ? "ja" : language) {
            case "en" -> List.of("en", "ja");
            case "zh-CN" -> List.of("zh-CN", "zh-TW", "en", "ja");
            case "zh-TW" -> List.of("zh-TW", "zh-CN", "en", "ja");
            case "ko" -> List.of("ko", "en", "ja");
            default -> List.of("ja", "en");
        };
    }
}
