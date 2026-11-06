package com.example.kakeibo;

import com.example.kakeibo.service.TextractService;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class ReceiptOcrService {

    // 合計を表す言葉。長い言葉から順に並べ、部分一致で誤って短い言葉を選ばないようにします。
    private static final List<String> TOTAL_WORDS = List.of(
            "GRAND TOTAL",
            "今回ご請求額",
            "今回のお支払金額",
            "今回のお支払い",
            "お支払い合計",
            "お支払金額",
            "お支払い金額",
            "お支払額",
            "お買上合計",
            "買上合計",
            "税込合計",
            "合計金額",
            "総合計",
            "総計",
            "ご請求額",
            "請求金額",
            "ご利用金額",
            "ご利用額",
            "お会計",
            "合計",
            "TOTAL",
            "AMOUNT DUE",
            "BALANCE DUE"
    );

    // 金額の候補。通貨記号（¥・\）が付いている数字を優先して使います。
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?<![0-9])(?:[¥\\\\]\\s*)?([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?(?![0-9])");

    // 日付・税率・電話番号・登録番号など、金額ではない数字を除外するためのパターンです。
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(20\\d{2})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})\\s*日?");

    private static final Pattern PERCENT_PATTERN = Pattern.compile(
            "[0-9]+(?:\\.[0-9]+)?\\s*%");

    private static final Pattern LONG_NUMBER_PATTERN = Pattern.compile(
            "(?i)(?:T\\d{10,}|\\d{10,}|\\d{2,4}-\\d{2,4}-\\d{2,4})");

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
                        "レシートの合計金額を読み取れませんでした。明るい場所で撮り直すか、手入力で登録してください。");
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

    private BigDecimal findAmount(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = normalizeLine(lines.get(i));

            // Textractが「合計」を「合」「計」の2行に分けることがあるため、その場合も合計行として扱います。
            boolean splitTotal = "合".equals(line)
                    && i + 1 < lines.size()
                    && "計".equals(normalizeLine(lines.get(i + 1)));
            String labelLine = splitTotal ? "合計" : line;

            if (isExcludedAmountLine(labelLine)) {
                continue;
            }

            String keyword = totalKeyword(labelLine);
            if (keyword == null) {
                continue;
            }

            // 同じ行に金額があれば、その金額を使います。
            BigDecimal amount = findAmountNearKeyword(labelLine, keyword);
            if (amount != null && amount.signum() > 0) {
                return amount;
            }

            // 合計ラベルの後ろ数行を確認します。税額の説明行を挟むレシートがあるためです。
            int start = splitTotal ? i + 2 : i + 1;
            for (int offset = start; offset <= i + 5 && offset < lines.size(); offset++) {
                String nextLine = normalizeLine(lines.get(offset));

                // お預かり・お釣り・ポイントの行まで来たら、合計金額ではないので探索を止めます。
                if (isPaymentLine(nextLine)) {
                    break;
                }
                if (isExcludedAmountLine(nextLine)) {
                    continue;
                }

                amount = extractAmount(nextLine);
                if (amount != null && amount.signum() > 0) {
                    return amount;
                }
            }
        }

        // 合計を特定できない場合は、誤った金額を返さない
        return null;
    }

    private String totalKeyword(String line) {
        // 「言十」はTextractが「計」を誤認識したものです。単独の「計」も合計行として扱います。
        if ("計".equals(line)) {
            return "計";
        }

        String upper = line.toUpperCase(Locale.ROOT);
        for (String word : TOTAL_WORDS) {
            if (upper.contains(word.toUpperCase(Locale.ROOT))) {
                return word;
            }
        }
        return null;
    }

    private String normalizeLine(String text) {
        if (text == null) {
            return "";
        }

        // 全角数字・全角記号を半角へそろえ、空白を除去します。
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\u3000]+", "");

        // Textractは「合計」を「合言十」「言十」と誤認識することがあります。
        return normalized
                .replace("合言十", "合計")
                .replace("言十", "計");
    }

    private BigDecimal findAmountNearKeyword(String line, String keyword) {
        String searchable = maskNonAmountNumbers(line);
        String upper = searchable.toUpperCase(Locale.ROOT);
        int keywordIndex = upper.indexOf(keyword.toUpperCase(Locale.ROOT));

        if (keywordIndex < 0) {
            return null;
        }

        Matcher matcher = AMOUNT_PATTERN.matcher(searchable);
        BigDecimal result = null;
        int bestScore = Integer.MAX_VALUE;

        while (matcher.find()) {
            BigDecimal value = toAmount(matcher);
            if (value == null || value.signum() <= 0) {
                continue;
            }

            int distance = matcher.start() >= keywordIndex
                    ? matcher.start() - (keywordIndex + keyword.length())
                    : keywordIndex - matcher.end();

            // キーワードに近い金額を優先し、通貨記号や桁区切りがある金額を強く優先します。
            int score = Math.max(distance, 0);
            if (isCurrencyAmount(matcher)) {
                score -= 1000;
            } else if (hasGrouping(matcher)) {
                score -= 100;
            }
            if (value.compareTo(BigDecimal.TEN) < 0) {
                score += 100;
            }

            if (score < bestScore) {
                bestScore = score;
                result = value;
            }
        }

        return result;
    }

    private BigDecimal extractAmount(String line) {
        String searchable = maskNonAmountNumbers(line);
        Matcher matcher = AMOUNT_PATTERN.matcher(searchable);
        BigDecimal result = null;
        int bestScore = Integer.MAX_VALUE;

        while (matcher.find()) {
            BigDecimal value = toAmount(matcher);
            if (value == null || value.signum() <= 0) {
                continue;
            }

            int score = isCurrencyAmount(matcher) ? 0 : hasGrouping(matcher) ? 10 : 20;
            if (score <= bestScore) {
                bestScore = score;
                result = value;
            }
        }

        return result;
    }

    private BigDecimal toAmount(Matcher matcher) {
        try {
            return new BigDecimal(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
            // 読み取れない数字は無視する
            return null;
        }
    }

    private boolean isCurrencyAmount(Matcher matcher) {
        String matched = matcher.group();
        return matched.startsWith("¥") || matched.startsWith("\\");
    }

    private boolean hasGrouping(Matcher matcher) {
        return matcher.group(1).contains(",");
    }

    private String maskNonAmountNumbers(String line) {
        String masked = DATE_PATTERN.matcher(line).replaceAll(" ");
        masked = PERCENT_PATTERN.matcher(masked).replaceAll(" ");
        return LONG_NUMBER_PATTERN.matcher(masked).replaceAll(" ");
    }

    private boolean isExcludedAmountLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);

        if (upper.contains("小計") || upper.contains("SUBTOTAL")) {
            return true;
        }
        if (upper.contains("お預かり") || upper.contains("お預り")
                || upper.contains("預り") || upper.contains("お釣り")
                || upper.contains("釣銭") || upper.contains("ポイント")
                || upper.contains("割引") || upper.contains("値引き")) {
            return true;
        }
        if (upper.contains("合計税額") || upper.contains("税額合計")) {
            return true;
        }

        // 合計の言葉がない行は、消費税・税額だけの説明行として除外します。
        boolean hasTotalWord = totalKeyword(line) != null;
        if (!hasTotalWord && (upper.contains("消費税")
                || upper.contains("税額") || upper.contains("税等"))) {
            return true;
        }

        return false;
    }

    private boolean isPaymentLine(String line) {
        return line.contains("お預かり")
                || line.contains("お預り")
                || line.contains("預り")
                || line.contains("お釣り")
                || line.contains("釣銭")
                || line.contains("ポイント");
    }

    private String findItemName(List<String> lines) {
        String fallback = null;

        for (int i = 0; i < lines.size(); i++) {
            String value = lines.get(i) == null ? "" : lines.get(i).trim();
            if (!isItemCandidate(value)) {
                continue;
            }

            if (fallback == null) {
                fallback = value;
            }

            // 商品名の直後にある金額行を手がかりに、店名や住所ではなく商品名を選びます。
            for (int offset = 1; offset <= 2 && i + offset < lines.size(); offset++) {
                if (isPriceLikeLine(lines.get(i + offset))) {
                    return value;
                }
            }
        }

        return fallback != null ? fallback : "レシート";
    }

    private boolean isItemCandidate(String value) {
        if (value.isEmpty() || value.length() > 30) {
            return false;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\u3000]+", "");

        if (normalized.matches(".*\\d{4}[-/年].*")
                || normalized.matches(".*\\d{1,2}月\\d{1,2}日.*")
                || normalized.matches(".*[0-9]{6,}.*")) {
            return false;
        }
        if (normalized.matches("^[0-9\\p{Punct}\\p{S}\\s]+$")) {
            return false;
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        return !containsAny(upper,
                "合計", "小計", "税", "預", "釣", "お支払", "領収", "レシート",
                "伝票", "電話", "TEL", "登録番号", "事業者", "レジ", "店",
                "住所", "商品", "ポイント", "会員", "買上", "ありがとう",
                "よろしく", "SHOP", "SEVEN", "セブン", "LAWSON", "ローソン",
                "FAMILY", "ファミリーマート", "ファミマ", "イレブン", "STORE",
                "県", "市", "区", "町", "丁目", "番地", "号", "通り");
    }

    // 商品名の直後の行が「金額だけの行」かどうかを判定します。
    // 「120G」「7P」のような商品名のサイズを金額と誤認しないようにします。
    private boolean isPriceLikeLine(String line) {
        if (line == null) {
            return false;
        }

        String searchable = maskNonAmountNumbers(normalizeLine(line));
        Matcher matcher = AMOUNT_PATTERN.matcher(searchable);
        List<int[]> amountRanges = new ArrayList<>();

        while (matcher.find()) {
            BigDecimal value = toAmount(matcher);
            if (value == null) {
                continue;
            }

            // 「120G」「400ml」のように英字が続く数字は、商品のサイズとして扱います。
            int end = matcher.end();
            if (end < searchable.length()
                    && isAsciiLetter(searchable.charAt(end))) {
                continue;
            }

            if (isCurrencyAmount(matcher) || hasGrouping(matcher)
                    || value.compareTo(BigDecimal.TEN) >= 0) {
                amountRanges.add(new int[] { matcher.start(), end });
            }
        }

        if (amountRanges.isEmpty()) {
            return false;
        }

        StringBuilder remaining = new StringBuilder();
        int last = 0;
        for (int[] range : amountRanges) {
            if (range[0] > last) {
                remaining.append(searchable, last, range[0]);
            }
            last = Math.max(last, range[1]);
        }
        if (last < searchable.length()) {
            remaining.append(searchable, last, searchable.length());
        }

        String rest = remaining.toString()
                .replaceAll("[*※\\s¥\\\\,./%()（）\\[\\]【】:：;；+\\-]", "")
                .replaceAll("(税込|税抜|軽|円|個|点|割引|引|対象|外|内|等)", "");
        return rest.length() <= 2;
    }

    private boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private LocalDate findDate(List<String> lines) {
        for (String line : lines) {
            if (line == null) {
                continue;
            }

            String normalized = Normalizer.normalize(line, Normalizer.Form.NFKC);
            Matcher matcher = DATE_PATTERN.matcher(normalized);

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
        String text = String.join(" ", lines.stream()
                .filter(line -> line != null)
                .toList());

        if (containsAny(text, "ホテル", "宿泊", "旅館", "民宿", "ゲストハウス")) {
            return "宿泊";
        }
        if (containsAny(text, "タクシー", "バス", "電車", "交通", "駅",
                "空港", "レンタカー", "ガソリン", "駐車")) {
            return "交通";
        }
        if (containsAny(text, "レストラン", "飲食", "カフェ", "食堂",
                "おにぎり", "弁当", "パン", "コーヒー", "飲料", "食品",
                "食料品", "スナック", "チョコ", "スープ", "アイス", "野菜")) {
            return "食事";
        }
        if (containsAny(text, "観光", "入場", "体験", "チケット", "施設", "ツアー")) {
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
            this.detail = describeCause(cause);
        }

        public String getDetail() {
            return detail;
        }

        private static String describeCause(Throwable cause) {
            if (cause == null) {
                return null;
            }

            // AWSのエラー種別と説明を画面に出し、権限・認証・通信のどこで失敗したか分かるようにします。
            String message = cause.getMessage();
            return cause.getClass().getSimpleName()
                    + (message == null || message.isBlank() ? "" : ": " + message);
        }
    }
}
