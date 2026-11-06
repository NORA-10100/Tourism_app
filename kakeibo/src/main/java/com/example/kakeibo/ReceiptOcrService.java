package com.example.kakeibo;

import com.example.kakeibo.service.TextractService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class ReceiptOcrService {

    private static final List<String> TOTAL_WORDS =
        List.of(
 "GRAND TOTAL",
                "今回ご請求額",
                "お支払い合計",
                "お支払い金額",
                "合計金額",
                "税込合計",
                "総合計",
                "ご請求額",
                "お会計",
                "合計",
                "TOTAL"
        );

    private final TextractService textractService;

    public ReceiptOcrService(TextractService textractService) {
        this.textractService = textractService;
    }

    public ReceiptData read(byte[] image, String contentType) {
        if (image == null || image.length == 0) {
            throw new ReceiptOcrException("画像が空です。");
        }

        try {            
            List<String> lines = textractService.readText(image);

            if (lines == null || lines.isEmpty()) {
                throw new ReceiptOcrException(
                        "レシートの文字を読み取れませんでした。");
            }
            String itemName = findItemName(lines);
            BigDecimal amount = findAmount(lines);

            if (amount == null || amount.signum() <= 0) {
                throw new ReceiptOcrException(
                        "レシートの合計金額を読み取れませんでした。");
            }

            LocalDate date = findDate(lines);
            String category = findCategory(lines);

            return new ReceiptData(
                    itemName, amount, date, category, "JPY");

        } catch (ReceiptOcrException e) {
            throw e;
        } catch (Exception e) {
            throw new ReceiptOcrException(
                    "AWS Textractに接続できません。AWS認証情報（AWS_PROFILEなど）、リージョン、ネットワークを確認してください。",
                    e);
        }
    }

    private String findItemName(List<String> lines) {
        for (String line : lines) {
            String value = line.trim();

            if (value.isEmpty()
                    || value.matches(".*\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*")
                    || value.matches(".*\\d{1,2}月\\d{1,2}日.*")) {
                continue;
            }

            if (value.length() <= 30
                    && !value.matches(".*[0-9]{3,}.*")
                    && !value.contains("合計")
                    && !value.contains("小計")) {
                return value;
            }
        }
        return "レシート";
    }

    private BigDecimal findAmount(List<String> lines) {

    // ① 合計キーワードを探す
    for (int i = 0; i < lines.size(); i++) {

        // OCRで読み取った文字列から空白を取り除く
        String line = normalizeAmountText(lines.get(i));
        String upper = line.toUpperCase(Locale.ROOT);

        // 小計・税額・お釣りなどの行は除外
        if (isExcludedAmountLine(upper)) {
            continue;
        }

        for (String word : TOTAL_WORDS) {

            int index = upper.indexOf(word.toUpperCase(Locale.ROOT));

            if (index >= 0) {

                // ② キーワードと同じ行から金額を取得
                BigDecimal amount = findAmountNearKeyword(line, word);

                if (amount != null && amount.signum() > 0) {
                    return amount;
                }

                // ③ 合計ラベルの後ろ数行を確認する。税額の説明行を挟むレシートがある。
                for (int offset = 1; offset <= 3 && i + offset < lines.size(); offset++) {
                    String nextLine = normalizeAmountText(lines.get(i + offset));
                    if (isExcludedAmountLine(nextLine.toUpperCase(Locale.ROOT))) {
                        continue;
                    }

                    amount = extractAmount(nextLine);
                    if (amount != null && amount.signum() > 0) {
                        return amount;
                    }
                }
            }
        }
    }

    // ④ 合計を特定できない場合は、誤った金額を返さない
    return null;
}

    private String normalizeAmountText(String text) {
        // 半角・全角空白を除去し、Textractで「合 言十」と誤認識された「合計」を補正する。
        return text.replaceAll("[\\s\\u3000]+", "")
                .replace("合言十", "合計");
    }

    private BigDecimal findAmountNearKeyword(
        String line, String keyword) {

    String upper = line.toUpperCase(Locale.ROOT);
    int keywordIndex = upper.indexOf(
            keyword.toUpperCase(Locale.ROOT));

    if (keywordIndex < 0) {
        return null;
    }

    Pattern pattern = Pattern.compile(
            "(?<!\\d)[0-9]{1,3}(?:,[0-9]{3})+(?:\\.\\d{1,2})?"
            + "|(?<!\\d)[0-9]+(?:\\.\\d{1,2})?"
    );

    Matcher matcher = pattern.matcher(line);

    BigDecimal result = null;
    int minDistance = Integer.MAX_VALUE;

    while (matcher.find()) {

        int distance;

        if (matcher.start() >= keywordIndex) {
            distance = matcher.start()
                    - (keywordIndex + keyword.length());
        } else {
            distance = keywordIndex - matcher.end();
        }

        if (distance < minDistance) {
            try {
                result = new BigDecimal(
                        matcher.group().replace(",", ""));

                minDistance = distance;

            } catch (NumberFormatException ignored) {
                // 読み取れない数字は無視する
            }
        }
    }

    return result;
}
    private boolean isExcludedAmountLine(String line) {

    String upper = line.toUpperCase(Locale.ROOT);

    return upper.contains("小計")
            || upper.contains("SUBTOTAL")
            || upper.contains("消費税")
            || upper.contains("税額")
            || upper.contains("お預かり")
            || upper.contains("預り")
            || upper.contains("お釣り")
            || upper.contains("釣銭")
            || upper.contains("割引")
            || upper.contains("値引き");
}


    private BigDecimal extractAmount(String line) {
        Matcher matcher = Pattern.compile(
                "(?<!\\d)[0-9]{1,3}(?:,[0-9]{3})+(?:\\.\\d{1,2})?"
                + "|(?<!\\d)[0-9]+(?:\\.\\d{1,2})?")
                .matcher(line);

        BigDecimal result = null;

        while (matcher.find()) {
            try {
                result = new BigDecimal(
                        matcher.group().replace(",", ""));
            } catch (NumberFormatException ignored) {
                // 読み取れない数字は無視する
            }
        }

        return result;
    }

    private LocalDate findDate(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = Pattern.compile(
                    "(20\\d{2})[-/年](\\d{1,2})[-/月](\\d{1,2})日?")
                    .matcher(line);

            if (matcher.find()) {
                try {
                    return LocalDate.of(
                            Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(3)));
                } catch (DateTimeException
                        | NumberFormatException e) {
                    // 不正な日付は無視する
                }
            }
        }

        return LocalDate.now();
    }

    private String findCategory(List<String> lines) {
        String text = String.join(" ", lines);

        if (text.contains("ホテル") || text.contains("宿泊")
                || text.contains("旅館")) {
            return "宿泊";
        }
        if (text.contains("タクシー") || text.contains("バス")
                || text.contains("電車") || text.contains("交通")) {
            return "交通";
        }
        if (text.contains("レストラン") || text.contains("飲食")
                || text.contains("カフェ") || text.contains("食堂")) {
            return "食事";
        }
        if (text.contains("観光") || text.contains("入場")
                || text.contains("体験")) {
            return "観光";
        }

        return "その他";
    }

    public record ReceiptData(
            String itemName,
            BigDecimal amount,
            LocalDate date,
            String category,
            String currency) {
    }

    public static class ReceiptOcrException extends RuntimeException {

        private final String detail;

        public ReceiptOcrException(String message) {
            this(message, null);
        }

        public ReceiptOcrException(String message, Throwable cause) {
            super(message, cause);
            this.detail = null;
        }

        public String getDetail() {
            return detail;
        }
    }
}
