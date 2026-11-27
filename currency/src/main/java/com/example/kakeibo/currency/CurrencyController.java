package com.example.kakeibo.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.kakeibo.common.SessionUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 通貨選択・レート表示（4-1-1）と料金換算（4-2-1）の画面を受け持ちます。
 */
@Controller
@RequestMapping("/currency")
public class CurrencyController {

    private static final String DEFAULT_CURRENCY = "USD";

    private final ExchangeRateService exchangeRateService;
    private final MessageSource messages;

    public CurrencyController(ExchangeRateService exchangeRateService,
            MessageSource messages) {
        this.exchangeRateService = exchangeRateService;
        this.messages = messages;
    }

    @GetMapping
    public String index(HttpSession session, Model model) {
        if (!SessionUser.isLoggedIn(session)) {
            return "redirect:/login";
        }
        String selectedCurrency = selectedCurrency(session);
        model.addAttribute("snapshot",
                exchangeRateService.snapshot(SessionUser.language(session)));
        model.addAttribute("selectedCurrency", selectedCurrency);
        model.addAttribute("fromCurrency", "JPY");
        model.addAttribute("toCurrency", selectedCurrency);
        return "currency/index";
    }

    // メモ: レート一覧を使い、従来の外貨から円への換算と外貨同士の換算に対応します。
    @PostMapping("/convert")
    public String convert(@RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpSession session, Model model) {
        if (!SessionUser.isLoggedIn(session)) {
            return "redirect:/login";
        }

        RateSnapshot snapshot = exchangeRateService.snapshot(SessionUser.language(session));
        String fromCode = from == null || from.isBlank()
                ? (currency == null || currency.isBlank() ? "JPY" : currency)
                : from;
        String toCode = to == null || to.isBlank() ? "JPY" : to;

        BigDecimal fromRate = rateToJpy(snapshot, fromCode);
        BigDecimal toRate = rateToJpy(snapshot, toCode);

        if (fromRate == null || toRate == null || amount == null || amount.signum() < 0) {
            show(session, model, selectedCurrency(session), message("currency.convert.error"));
            model.addAttribute("inputAmount", amount);
            model.addAttribute("fromCurrency", fromCode);
            model.addAttribute("toCurrency", toCode);
            return "currency/index";
        }

        int fractionDigits = fractionDigits(toCode);
        BigDecimal convertedAmount = amount.multiply(fromRate)
                .divide(toRate, 8, RoundingMode.HALF_UP)
                .setScale(fractionDigits, RoundingMode.HALF_UP);
        session.setAttribute("selectedCurrency", "JPY".equals(toCode) ? fromCode : toCode);

        model.addAttribute("snapshot", snapshot);
        model.addAttribute("inputAmount", amount);
        model.addAttribute("selectedCurrency", selectedCurrency(session));
        model.addAttribute("fromCurrency", fromCode);
        model.addAttribute("toCurrency", toCode);
        model.addAttribute("convertedAmount", convertedAmount);
        return "currency/index";
    }

    private static BigDecimal rateToJpy(RateSnapshot snapshot, String currencyCode) {
        if ("JPY".equals(currencyCode)) {
            return BigDecimal.ONE;
        }
        return snapshot.rates().stream()
                .filter(rate -> rate.code().equals(currencyCode))
                .map(RateSnapshot.RateView::rate)
                .findFirst()
                .orElse(null);
    }

    // メモ: 数値変換できない入力も画面内のエラーとして案内します。
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String handleConversionTypeMismatch(HttpServletRequest request,
            HttpSession session, Model model) {
        if (!SessionUser.isLoggedIn(session)) {
            return "redirect:/login";
        }
        String legacyCurrency = request.getParameter("currency");
        String from = request.getParameter("from");
        String to = request.getParameter("to");
        String selected = legacyCurrency == null ? selectedCurrency(session) : legacyCurrency;
        show(session, model, selected,
                message("currency.convert.error"));
        model.addAttribute("inputAmount", request.getParameter("amount"));
        model.addAttribute("fromCurrency",
                from == null || from.isBlank() ? selected : from);
        model.addAttribute("toCurrency", to == null || to.isBlank() ? "JPY" : to);
        return "currency/index";
    }

    // メモ: レート一覧と選択通貨を共通で画面へ渡し、初期表示とエラー表示で再利用します。
    private String show(HttpSession session, Model model, String selectedCurrency) {
        return show(session, model, selectedCurrency, null);
    }

    private String show(HttpSession session, Model model, String selectedCurrency, String error) {
        model.addAttribute("snapshot",
                exchangeRateService.snapshot(SessionUser.language(session)));
        model.addAttribute("selectedCurrency", selectedCurrency);
        model.addAttribute("fromCurrency", "JPY");
        model.addAttribute("toCurrency", selectedCurrency);
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "currency/index";
    }

    // メモ: セッション値がない初回表示では、換算フォームの既定通貨を使います。
    private static String selectedCurrency(HttpSession session) {
        Object value = session.getAttribute("selectedCurrency");
        return value instanceof String code ? code : DEFAULT_CURRENCY;
    }

    private static int fractionDigits(String currencyCode) {
        int digits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        return digits < 0 ? 2 : digits;
    }

    // メモ: 入力エラーも画面の表示言語に合わせます。
    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
