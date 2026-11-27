package com.example.kakeibo.currency;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.example.kakeibo.language.LanguageSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 為替レートを外部 API から取得し、DB にキャッシュします。
 * 取得に失敗した場合は DB の前回レート、それも無ければ参考の固定値を返します。
 *
 * <p>既定の取得先は open.er-api.com（API キー不要）です。
 * 別のサービスを使う場合は {@code vita.currency.api-url} を変更します。</p>
 */
@Service
public class ExchangeRateService {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final List<String> CODES = List.of("USD", "EUR", "CNY", "KRW", "TWD");

    private static final Map<String, String> FLAGS = Map.of(
            "USD", "🇺🇸",
            "EUR", "🇪🇺",
            "CNY", "🇨🇳",
            "KRW", "🇰🇷",
            "TWD", "🇹🇼");

    /** 外部 API に接続できないときの参考レート（1通貨あたりの円）。 */
    private static final Map<String, BigDecimal> FALLBACK = Map.of(
            "USD", new BigDecimal("149.32"),
            "EUR", new BigDecimal("160.28"),
            "CNY", new BigDecimal("20.55"),
            "KRW", new BigDecimal("0.11"),
            "TWD", new BigDecimal("4.62"));

    private static final DateTimeFormatter UPDATED_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final ExchangeRateRepository repository;
    private final MessageSource messages;
    private final boolean remoteEnabled;
    private final String apiUrl;
    private final long cacheMinutes;
    private final HttpClient httpClient;
    private final JsonMapper mapper = JsonMapper.builder().build();

    private volatile LocalDateTime lastFetchAt;
    private volatile boolean lastFetchSucceeded;

    public ExchangeRateService(ExchangeRateRepository repository,
            MessageSource messages,
            @Value("${vita.currency.remote-enabled:true}") boolean remoteEnabled,
            @Value("${vita.currency.api-url:https://open.er-api.com/v6/latest/JPY}") String apiUrl,
            @Value("${vita.currency.cache-minutes:10}") long cacheMinutes) {
        this.repository = repository;
        this.messages = messages;
        this.remoteEnabled = remoteEnabled;
        this.apiUrl = apiUrl;
        this.cacheMinutes = cacheMinutes;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public RateSnapshot snapshot(String language) {
        // メモ: 取得間隔を守り、最新・DBキャッシュ・参考値の順で表示データを選びます。
        LocalDateTime now = LocalDateTime.now();
        if (remoteEnabled && shouldRefresh(now)) {
            try {
                refresh();
                lastFetchAt = now;
                lastFetchSucceeded = true;
            } catch (IOException | InterruptedException | IllegalArgumentException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                logger.warn("為替レートの外部取得に失敗しました。保存済みデータまたは参考レートを使用します。", e);
                lastFetchAt = now;
                lastFetchSucceeded = false;
            }
        }

        List<ExchangeRate> cached = repository.findAllByOrderByCurrencyCodeAsc();
        if (!cached.isEmpty()) {
            RateSnapshot.Source source = isFresh(now)
                    ? RateSnapshot.Source.LIVE
                    : RateSnapshot.Source.CACHE;
            return new RateSnapshot(toViews(cached, language), source, updatedAtLabel(cached));
        }

        return new RateSnapshot(fallbackViews(language),
                RateSnapshot.Source.FALLBACK, null);
    }

    private boolean shouldRefresh(LocalDateTime now) {
        // メモ: 初回取得またはキャッシュ期限切れの場合だけ外部 API を呼び出します。
        return lastFetchAt == null
                || lastFetchAt.plusMinutes(cacheMinutes).isBefore(now);
    }

    private boolean isFresh(LocalDateTime now) {
        // メモ: 今回の外部取得に成功し、キャッシュ期限内なら最新レートとして扱います。
        return lastFetchSucceeded && lastFetchAt != null
                && lastFetchAt.plusMinutes(cacheMinutes).isAfter(now);
    }

    /** open.er-api.com は「1円あたりの通貨量」を返すため、円換算に逆数を使います。 */
    private void refresh() throws IOException, InterruptedException {
        // メモ: 通信と応答の検証が済んだレートだけをまとめてDBへ保存します。
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("currency API status: " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
        if (!"success".equals(root.path("result").asString())) {
            throw new IOException("currency API did not return success");
        }

        JsonNode rates = root.path("rates");
        List<ExchangeRate> updates = new ArrayList<>();

        for (String code : CODES) {
            JsonNode value = rates.get(code);
            if (value == null || !value.isNumber()) {
                continue;
            }
            double perJpy = value.asDouble();
            if (perJpy <= 0) {
                continue;
            }

            BigDecimal rate = BigDecimal.valueOf(1.0 / perJpy)
                    .setScale(4, RoundingMode.HALF_UP);
            ExchangeRate entity = repository.findByCurrencyCode(code)
                    .orElseGet(() -> new ExchangeRate(code, rate));
            entity.update(rate);
            updates.add(entity);
        }

        if (updates.isEmpty()) {
            throw new IOException("currency API returned no supported rates");
        }
        repository.saveAll(updates);
    }

    private List<RateSnapshot.RateView> toViews(List<ExchangeRate> cached, String language) {
        // メモ: 対応通貨の表示順を固定し、DBにない通貨は参考値で補います。
        Locale locale = LanguageSupport.toLocale(language);
        List<RateSnapshot.RateView> views = new ArrayList<>();

        for (String code : CODES) {
            BigDecimal rate = cached.stream()
                    .filter(item -> code.equals(item.getCurrencyCode()))
                    .map(ExchangeRate::getRateToJpy)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(FALLBACK.get(code));
            views.add(new RateSnapshot.RateView(code, name(code, locale), FLAGS.get(code), rate));
        }
        return views;
    }

    private List<RateSnapshot.RateView> fallbackViews(String language) {
        Locale locale = LanguageSupport.toLocale(language);
        return CODES.stream()
                .map(code -> new RateSnapshot.RateView(
                        code, name(code, locale), FLAGS.get(code), FALLBACK.get(code)))
                .toList();
    }

    private String name(String code, Locale locale) {
        return messages.getMessage("currency.name." + code, null, code, locale);
    }

    private static String updatedAtLabel(List<ExchangeRate> cached) {
        return cached.stream()
                .map(ExchangeRate::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .map(UPDATED_FORMAT::format)
                .orElse(null);
    }
}
