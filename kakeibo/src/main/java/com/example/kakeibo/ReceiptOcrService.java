package com.example.kakeibo;

import com.example.kakeibo.service.TextractService;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class ReceiptOcrService {

    // 品目名として読み取るレシート上部の行数です。
    private static final int STORE_NAME_SCAN_LIMIT = 10;

    // レシート上部の店舗名を判定する言葉です。
    private static final List<String> STORE_WORDS = List.of(
            "店", "ストア", "マート", "コンビニ", "スーパー", "ドラッグ",
            "ショップ", "カフェ", "レストラン", "食堂", "ホテル", "旅館",
            "タクシー", "SHOP", "STORE", "MARKET", "MART", "CAFE",
            "HOTEL", "RESTAURANT");

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

    // Textractは「合計 ¥518 8%」を「合計 ¥5188%」のように金額と税率を連結して返すことがあります。
    // 税率（8% / 10%）を金額から切り離すためのパターンです。
    private static final Pattern JOINED_TAX_RATE_PATTERN = Pattern.compile(
            "([0-9][0-9,]*)(?:8|10)\\s*%");

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

            String itemName = findStoreName(lines);
            BigDecimal amount = findAmount(lines);

            if (amount == null || amount.signum() <= 0) {
                throw new ReceiptOcrException(
                        "レシートの合計金額を読み取れませんでした。明るい場所で撮り直すか、手入力で登録してください。");
            }

            LocalDate date = findDate(lines);
            String category = findCategory(lines, itemName);

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
        // 合計 → 合 → 計 の順に優先します。
        // 小計は isExcludedAmountLine で除外し、合計がある場合は必ず合計を採用します。
        for (int priority = 1; priority <= 3; priority++) {
            BigDecimal amount = findAmountByPriority(lines, priority);
            if (amount != null) {
                return amount;
            }
        }

        // 合計ラベルを読み取れないレシートでも結果を出せるよう、
        // 小計・税・お預かり等を除いた金額のうち最大のものを合計として扱います。
        BigDecimal fallback = findLargestPlausibleAmount(lines);
        if (fallback != null) {
            System.out.println("===== レシートOCR: 合計ラベルを特定できず最大金額を使用 =====");
            System.out.println("amount=" + fallback);
            System.out.println("=============================");
        } else {
            System.out.println("===== レシートOCR: 金額を特定できませんでした =====");
            lines.forEach(System.out::println);
            System.out.println("=============================");
        }
        return fallback;
    }

    private BigDecimal findAmountByPriority(List<String> lines, int wantedPriority) {
        for (int i = 0; i < lines.size(); i++) {
            String rawLine = lines.get(i);
            String line = normalizeLine(rawLine);

            if (line.isEmpty() || isExcludedAmountLine(line)) {
                continue;
            }

            // Textractが「合計」を「合」「計」の2行に分けることがあります。
            boolean splitTotal = "合".equals(line)
                    && i + 1 < lines.size()
                    && "計".equals(normalizeLine(lines.get(i + 1)));
            String labelLine = splitTotal ? "合計" : line;

            String keyword = totalKeyword(labelLine);
            if (keyword == null || totalPriority(keyword) != wantedPriority) {
                continue;
            }

            // 同じ行に金額があれば、その金額を使います。
            // 税率を先にマスクしてから空白を詰めることで、
            // 「合計 160 8%」が「合計1608%」に連結されて金額ごと消えるのを防ぎます。
            BigDecimal amount = findAmountNearKeyword(normalizeAmountLine(rawLine), keyword);
            if (amount != null && amount.signum() > 0) {
                return amount;
            }

            // 合計ラベルの後ろ数行を確認します。税額の説明行を挟むレシートがあるためです。
            for (int offset = 1; offset <= 5 && i + offset < lines.size(); offset++) {
                String nextRawLine = lines.get(i + offset);
                String nextLine = normalizeLine(nextRawLine);

                // お預かり・お釣り・ポイントの行まで来たら、合計金額ではないので探索を止めます。
                if (isPaymentLine(nextLine)) {
                    break;
                }
                if (isExcludedAmountLine(nextLine)) {
                    continue;
                }

                amount = extractAmount(normalizeAmountLine(nextRawLine));
                if (amount != null && amount.signum() > 0) {
                    return amount;
                }
            }
        }
        return null;
    }

    private BigDecimal findLargestPlausibleAmount(List<String> lines) {
        BigDecimal largest = null;

        for (String rawLine : lines) {
            String line = normalizeLine(rawLine);
            if (line.isEmpty() || isExcludedAmountLine(line) || isPaymentLine(line)) {
                continue;
            }

            // 日付・税率・登録番号などを除いた金額だけを候補にします。
            BigDecimal amount = extractAmount(normalizeAmountLine(rawLine));
            if (amount != null && amount.signum() > 0
                    && (largest == null || amount.compareTo(largest) > 0)) {
                largest = amount;
            }
        }

        return largest;
    }

    private String totalKeyword(String line) {
        String upper = line.toUpperCase(Locale.ROOT);

        // 「合計」「税込合計」「お支払い合計」など、明示的な合計を最優先します。
        for (String word : TOTAL_WORDS) {
            if (upper.contains(word.toUpperCase(Locale.ROOT))) {
                return word;
            }
        }

        // 合計の「合」だけが読めた場合は合計として扱います。
        // 「合計」は上で見つかるため、ここでは「合 ¥160」のような行が対象です。
        if ("合".equals(line) || line.startsWith("合") || line.endsWith("合")) {
            return "合";
        }

        // Textractが「計」を「言十」と誤認識した場合も、normalizeLine で「計」に直しています。
        // 「小計」は isExcludedAmountLine で除外済みです。
        if ("計".equals(line) || line.startsWith("計") || line.endsWith("計")) {
            return "計";
        }
        return null;
    }

    private int totalPriority(String keyword) {
        if ("合".equals(keyword)) {
            return 2;
        }
        if ("計".equals(keyword)) {
            return 3;
        }
        return 1;
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

    // 金額判定用の正規化。空白を詰める前に日付・税率・登録番号をマスクします。
    // 先に詰めると「160 8%」が「1608%」になり、税率の除去で金額まで消えてしまうためです。
    private String normalizeAmountLine(String text) {
        if (text == null) {
            return "";
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);

        // 「¥5188%」のように金額と税率が連結されている場合は、税率だけを取り除きます。
        // 例: 「¥1,1888%」→「¥1,188」、「¥108010%」→「¥1,080」
        normalized = JOINED_TAX_RATE_PATTERN.matcher(normalized).replaceAll("$1");

        String masked = maskNonAmountNumbers(normalized);

        return masked
                .replaceAll("[\\s\\u3000]+", "")
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
        if (upper.contains("合計税額") || upper.contains("税額合計")
                || upper.contains("消費税等合計") || upper.contains("消費税合計")) {
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

    // レシート上部に印字されている会社名・店舗名を品目名として使います。
    // 店名が「領収書」より上にある場合はその上の大きな文字を優先し、
    // 「領収書」が一番上にある場合はその下から店名を探します。
    private String findStoreName(List<String> lines) {
        int limit = Math.min(lines.size(), STORE_NAME_SCAN_LIMIT);
        int heading = findReceiptHeadingIndex(lines, limit);

        if (heading >= 0) {
            String above = selectStoreName(lines, 0, heading);
            if (above != null) {
                return above;
            }
            String below = selectStoreName(lines, heading + 1, limit);
            if (below != null) {
                return below;
            }
        } else {
            String found = selectStoreName(lines, 0, limit);
            if (found != null) {
                return found;
            }
        }

        return "レシート";
    }

    private int findReceiptHeadingIndex(List<String> lines, int limit) {
        for (int i = 0; i < limit; i++) {
            if (isReceiptHeadingLine(lines.get(i))) {
                return i;
            }
        }
        return -1;
    }

    // 指定した範囲から店舗名を選びます。
    // レシートは会社名・ブランド名が一番上に印字されるため、最初に見つかった行を店名として採用します。
    // 後ろの行にある「○○店」は支店名なので、ブランド名に結合しません。
    private String selectStoreName(List<String> lines, int from, int to) {
        for (int i = from; i < to; i++) {
            String value = cleanupStoreName(lines.get(i));
            if (isStoreNameCandidate(value)) {
                return value;
            }
        }
        return null;
    }

    private String cleanupStoreName(String text) {
        if (text == null) {
            return "";
        }

        String value = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\u3000]+", " ")
                .trim();

        // 「領 収 書」「【領収証】」のような見出し行は店名ではありません。
        if (isReceiptHeadingLine(value)) {
            return "";
        }
        value = value.replaceFirst("^(領収書|領収証|レシート)[\\s:：]*", "");

        // 入力欄の上限に合わせます。
        if (value.length() > 50) {
            value = value.substring(0, 50).trim();
        }
        return value;
    }

    // 「領収書」「領収証」「レシート」だけの行かどうかを判定します。
    // 店名が続く行（例:「領収書 テストマート」）は見出し扱いしません。
    private boolean isReceiptHeadingLine(String text) {
        if (text == null) {
            return false;
        }

        String compact = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\u3000]+", "")
                .replaceAll("[【】\\[\\]（）()「」『』]", "");

        return compact.matches("(領収書|領収証|レシート)控?");
    }

    private boolean isStoreNameCandidate(String value) {
        if (value.isEmpty() || value.length() > 50) {
            return false;
        }

        String upper = value.toUpperCase(Locale.ROOT);

        // 日付・電話・登録番号・レジ情報などは会社名ではありません。
        if (value.matches(".*\\d{4}[-/年].*")
                || value.matches(".*\\d{1,2}月\\d{1,2}日.*")
                || value.matches(".*[0-9]{6,}.*")
                || value.matches("^[0-9\\p{Punct}\\p{S}\\s]+$")) {
            return false;
        }
        if (containsAny(upper,
                "電話", "TEL", "登録番号", "事業者", "レジ", "伝票",
                "領収書", "領収証", "レシート", "お買上", "ありがとう",
                "ポイント", "会員", "店コード", "FAX",
                "HOLDINGS", "ホールディングス", "持株会社")) {
            return false;
        }

        // OCR がロゴや飾り文字を「了i」のような短い断片で返すことがあるため除外します。
        if (value.length() <= 2 && upper.matches(".*[A-Z0-9].*")) {
            return false;
        }

        // 住所行は除外します。ただし「○○店」のように店舗名を含む行は残します。
        boolean hasStoreWord = containsAny(upper, STORE_WORDS);
        if (!hasStoreWord && containsAny(upper,
                "県", "市", "区", "町", "丁目", "番地", "号", "通り")) {
            return false;
        }

        return true;
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, List<String> words) {
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

    private String findCategory(List<String> lines, String storeName) {
        String text = (storeName == null ? "" : storeName + " ")
                + String.join(" ", lines.stream()
                        .filter(line -> line != null)
                        .toList());

        if (containsAny(text, "ホテル", "宿泊", "旅館", "民宿", "ゲストハウス", "ペンション")) {
            return "宿泊";
        }
        if (containsAny(text, "タクシー", "バス", "電車", "交通", "駅",
                "空港", "レンタカー", "ガソリン", "駐車", "高速", "フェリー", "船")) {
            return "交通";
        }
        if (containsAny(text,
                "レストラン", "飲食", "カフェ", "食堂", "おにぎり", "弁当", "パン",
                "コーヒー", "飲料", "食品", "食料品", "スナック", "チョコ", "スープ",
                "アイス", "野菜", "果物", "フルーツ", "肉", "魚", "サバ", "米",
                "菓子", "ジュース", "お茶", "茶", "牛乳", "乳製品", "デザート",
                "カレー", "ラーメン", "そば", "うどん", "寿司", "惣菜", "サンド",
                "セブン", "イレブン", "ローソン", "ファミリーマート", "ファミマ",
                "コンビニ", "マート", "スーパー", "イオン", "サンエー", "マックスバリュ")) {
            return "食事";
        }
        if (containsAny(text, "観光", "入場", "体験", "チケット", "施設", "ツアー",
                "美術館", "博物館", "公園", "水族館")) {
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
