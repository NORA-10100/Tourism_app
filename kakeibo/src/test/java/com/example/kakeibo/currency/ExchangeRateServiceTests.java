package com.example.kakeibo.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.kakeibo.currency.RateSnapshot.Source;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

class ExchangeRateServiceTests {

    private static final String FRANKFURTER_RESPONSE = """
            [
              {"date":"2026-11-27","base":"JPY","quote":"USD","rate":0.0065},
              {"date":"2026-11-27","base":"JPY","quote":"EUR","rate":0.0059},
              {"date":"2026-11-27","base":"JPY","quote":"CNY","rate":0.0468},
              {"date":"2026-11-27","base":"JPY","quote":"KRW","rate":9.0},
              {"date":"2026-11-27","base":"JPY","quote":"TWD","rate":0.217},
              {"date":"2026-11-27","base":"JPY","quote":"HKD","rate":0.05}
            ]
            """;

    @Test
    void fetchesAtMostOncePerDayAndReusesDatabaseRatesAfterRestart() throws IOException {
        AtomicInteger apiCalls = new AtomicInteger();
        byte[] response = FRANKFURTER_RESPONSE.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/rates", exchange -> {
            apiCalls.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();

        try {
            ExchangeRateRepository repository = mock(ExchangeRateRepository.class);
            List<ExchangeRate> databaseRates = new ArrayList<>();
            when(repository.findAllByOrderByCurrencyCodeAsc())
                    .thenAnswer(invocation -> List.copyOf(databaseRates));
            when(repository.findByCurrencyCode(anyString())).thenAnswer(invocation ->
                    databaseRates.stream()
                            .filter(rate -> rate.getCurrencyCode().equals(invocation.getArgument(0)))
                            .findFirst());
            when(repository.saveAll(any())).thenAnswer(invocation -> {
                Iterable<ExchangeRate> updates = invocation.getArgument(0);
                List<ExchangeRate> saved = new ArrayList<>();
                for (ExchangeRate update : updates) {
                    databaseRates.removeIf(rate ->
                            rate.getCurrencyCode().equals(update.getCurrencyCode()));
                    databaseRates.add(update);
                    saved.add(update);
                }
                return saved;
            });

            MessageSource messages = mock(MessageSource.class);
            when(messages.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenAnswer(invocation -> invocation.getArgument(2));

            String apiUrl = "http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v2/rates?base=JPY";
            ExchangeRateService service =
                    new ExchangeRateService(repository, messages, true, apiUrl);

            RateSnapshot first = service.snapshot("ja");
            RateSnapshot second = service.snapshot("ja");
            RateSnapshot afterRestart =
                    new ExchangeRateService(repository, messages, true, apiUrl).snapshot("ja");

            assertEquals(Source.LIVE, first.source());
            assertEquals(Source.LIVE, second.source());
            assertEquals(Source.CACHE, afterRestart.source());
            assertEquals(6, first.rates().size());
            assertEquals(new BigDecimal("153.8462"), first.rates().stream()
                    .filter(rate -> rate.code().equals("USD"))
                    .findFirst()
                    .orElseThrow()
                    .rate());
            assertEquals(1, apiCalls.get());
        } finally {
            server.stop(0);
        }
    }
}
