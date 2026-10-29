package com.example.kakeibo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * レシート画像を Gemini API に送り、金額・品目・日付などを読み取るサービス。
 *
 * 必要な設定（application-local.properties か環境変数）:
 *   gemini.api.key=取得したAPIキー
 *   gemini.api.model=gemini-2.5-flash   （任意。未指定ならこの値）
 */
@Service
public class ReceiptOcrService {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final Set<String> CATEGORIES =
            Set.of("食事", "交通", "宿泊", "観光", "その他");

    private static final String PROMPT = """
            あなたはレシート・領収書の読み取り専門家です。
            画像に写っているレシートから次の項目を読み取り、JSONオブジェクトだけを返してください。

            {
              "itemName": "店名または主な購入品目（30文字以内）",
              "amount": 合計金額（数値のみ。カンマ・円記号・空白は含めない）,
              "date": "購入日（YYYY-MM-DD）。不明なら null",
              "category": "食事・交通・宿泊・観光・その他のいずれか",
              "currency": "通貨コード（JPY, USD, EUR, CNY, KRW など）"
            }

            ルール:
            - 「合計」「お会計」「税込合計」「TOTAL」の金額を優先する。
            - 小計・消費税・お預かり・お釣りは合計金額に使わない。
            - 金額が読み取れない場合は amount を 0 にする。
            - 説明文やMarkdownのコードブロックは付けず、JSONのみを返す。
            """;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String model;

    public ReceiptOcrService(
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.api.model:gemini-2.5-flash}") String model) {
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? "gemini-2.5-flash" : model.trim();
    }

    /**
     * レシート画像を読み取る。
     *
     * @param image       画像のバイト列
     * @param contentType MIMEタイプ（image/jpeg など）
     * @return 読み取り結果
     * @throws ReceiptOcrException 読み取りに失敗したとき
     */
    public ReceiptData read(byte[] image, String contentType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ReceiptOcrException(
                    "Gemini APIキーが設定されていません。application-local.properties に gemini.api.key を設定してください。");
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(
                                Map.of("text", PROMPT),
                                Map.of("inline_data", Map.of(
                                        "mime_type", contentType,
                                        "data", Base64.getEncoder().encodeToString(image)))))),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "responseMimeType", "application/json"));

        String raw = callApi(body);
        JsonNode data = parseModelJson(raw);

        String itemName = text(data, "itemName");
        if (itemName.isBlank()) {
            itemName = "レシート";
        }

        BigDecimal amount = parseAmount(text(data, "amount"));
        if (amount == null || amount.signum() <= 0) {
            throw new ReceiptOcrException(
                    "レシートの合計金額を読み取れませんでした。明るい場所で撮り直してください。");
        }

        LocalDate date = parseDate(text(data, "date"));
        String category = normalizeCategory(text(data, "category"));
        String currency = normalizeCurrency(text(data, "currency"));

        return new ReceiptData(itemName, amount, date, category, currency);
    }

    private String callApi(Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri(String.format(ENDPOINT, model))
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            if (detail != null && detail.length() > 300) {
                detail = detail.substring(0, 300);
            }
            throw new ReceiptOcrException(
                    "レシート読み取りAPIでエラーが発生しました（HTTP " + e.getStatusCode().value() + "）。",
                    "API応答: " + detail,
                    e);
        } catch (ReceiptOcrException e) {
            throw e;
        } catch (Exception e) {
            throw new ReceiptOcrException(
                    "レシート読み取りAPIに接続できませんでした。ネットワーク接続を確認してください。", e);
        }
    }

    private JsonNode parseModelJson(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            String text = root.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText("");

            String json = stripCodeFence(text);
            if (json.isBlank()) {
                throw new ReceiptOcrException("レシートの内容を読み取れませんでした。もう一度お試しください。");
            }
            return objectMapper.readTree(json);
        } catch (ReceiptOcrException e) {
            throw e;
        } catch (Exception e) {
            throw new ReceiptOcrException("レシートの読み取り結果を解析できませんでした。", e);
        }
    }

    private static String stripCodeFence(String text) {
        String s = text == null ? "" : text.trim();
        if (s.startsWith("```")) {
            int firstNewLine = s.indexOf('\n');
            if (firstNewLine >= 0) {
                s = s.substring(firstNewLine + 1);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
        }
        return s.trim();
    }

    private static String text(JsonNode data, String field) {
        JsonNode node = data.get(field);
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("").trim();
    }

    private static BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[^0-9.]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return LocalDate.now();
        }
        String normalized = value.replace('/', '-');

        // 2026-10-29 / 2026-10-29T12:00:00 の両方に対応
        if (normalized.length() > 10) {
            normalized = normalized.substring(0, 10);
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }

    private static String normalizeCategory(String value) {
        if (value == null || value.isBlank()) {
            return "その他";
        }
        String v = value.trim();
        if (CATEGORIES.contains(v)) {
            return v;
        }
        if (v.contains("食") || v.contains("飲食") || v.contains("レストラン") || v.contains("カフェ")) {
            return "食事";
        }
        if (v.contains("交通") || v.contains("タクシー") || v.contains("バス") || v.contains("電車") || v.contains("レンタカー")) {
            return "交通";
        }
        if (v.contains("宿泊") || v.contains("ホテル") || v.contains("旅館")) {
            return "宿泊";
        }
        if (v.contains("観光") || v.contains("入場") || v.contains("体験") || v.contains("お土産")) {
            return "観光";
        }
        return "その他";
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return "JPY";
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (v.contains("円") || v.equals("¥") || v.equals("JPY")) {
            return "JPY";
        }
        if (v.matches("[A-Z]{3}")) {
            return v;
        }
        if (v.contains("$") || v.contains("USD") || v.contains("ドル")) {
            return "USD";
        }
        if (v.contains("€") || v.contains("EUR") || v.contains("ユーロ")) {
            return "EUR";
        }
        return "JPY";
    }

    /**
     * レシート読み取り結果。
     */
    public record ReceiptData(
            String itemName,
            BigDecimal amount,
            LocalDate date,
            String category,
            String currency) {
    }

    /**
     * レシート読み取りに失敗したことを表す例外。
     */
    public static class ReceiptOcrException extends RuntimeException {

        private final String detail;

        public ReceiptOcrException(String message) {
            this(message, null, null);
        }

        public ReceiptOcrException(String message, Throwable cause) {
            this(message, null, cause);
        }

        public ReceiptOcrException(String message, String detail, Throwable cause) {
            super(message, cause);
            this.detail = detail;
        }

        public String getDetail() {
            return detail;
        }
    }
}
