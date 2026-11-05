package com.example.kakeibo.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/currency")
// メモ: 為替レートの表示と簡単な円換算画面を受け持ちます。
public class CurrencyController {

    // メモ: 外部API接続前の画面見本として、例のレートを表示します。
    private static final Map<String, BigDecimal> YEN_PER_UNIT = Map.of(
            "USD", new BigDecimal("149.32"),
            "EUR", new BigDecimal("160.28"),
            "KRW", new BigDecimal("0.11"),
            "TWD", new BigDecimal("4.62"));

    // メモ: ログイン中だけ為替画面を表示します。
    @GetMapping
    public String currency(HttpSession session, Model model) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }
        model.addAttribute("rates", YEN_PER_UNIT);
        return "currency";
    }

    // メモ: 入力金額に選択したレートを掛けて、日本円に換算します。
    @PostMapping("/convert")
    public String convert(@RequestParam BigDecimal amount,
                          @RequestParam String currency,
                          HttpSession session,
                          Model model) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }

        model.addAttribute("rates", YEN_PER_UNIT);
        BigDecimal rate = YEN_PER_UNIT.get(currency);
        if (rate == null || amount.signum() < 0) {
            model.addAttribute("error", "金額と通貨を確認してください。");
            return "currency";
        }

        BigDecimal yenAmount = amount.multiply(rate).setScale(0, RoundingMode.HALF_UP);
        model.addAttribute("convertedAmount", yenAmount);
        model.addAttribute("inputAmount", amount);
        model.addAttribute("selectedCurrency", currency);
        return "currency";
    }
}
