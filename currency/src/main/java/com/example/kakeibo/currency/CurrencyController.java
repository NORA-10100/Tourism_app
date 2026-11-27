package com.example.kakeibo.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
        return show(session, model, selectedCurrency(session));
    }

    // メモ: 画面に表示しているレートを使い、入力値を検証して円換算します。
    @PostMapping("/convert")
    public String convert(@RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String currency,
            HttpSession session, Model model) {
        if (!SessionUser.isLoggedIn(session)) {
            return "redirect:/login";
        }

        RateSnapshot snapshot = exchangeRateService.snapshot(SessionUser.language(session));
        String code = currency == null || currency.isBlank()
                ? selectedCurrency(session)
                : currency;

        RateSnapshot.RateView rate = snapshot.rates().stream()
                .filter(item -> item.code().equals(code))
                .findFirst()
                .orElse(null);

        if (rate == null || amount == null || amount.signum() < 0) {
            show(session, model, code, message("currency.convert.error"));
            model.addAttribute("inputAmount", amount);
            return "currency/index";
        }

        BigDecimal yen = amount.multiply(rate.rate())
                .setScale(0, RoundingMode.HALF_UP);
        session.setAttribute("selectedCurrency", code);

        model.addAttribute("snapshot", snapshot);
        model.addAttribute("inputAmount", amount);
        model.addAttribute("selectedCurrency", code);
        model.addAttribute("convertedAmount", yen);
        return "currency/index";
    }

    // メモ: 数値変換できない入力も画面内のエラーとして案内します。
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String handleConversionTypeMismatch(HttpServletRequest request,
            HttpSession session, Model model) {
        if (!SessionUser.isLoggedIn(session)) {
            return "redirect:/login";
        }
        String code = request.getParameter("currency");
        show(session, model, code == null ? selectedCurrency(session) : code,
                message("currency.convert.error"));
        model.addAttribute("inputAmount", request.getParameter("amount"));
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

    // メモ: 入力エラーも画面の表示言語に合わせます。
    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
