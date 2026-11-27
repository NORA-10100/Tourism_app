package com.example.kakeibo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;

/**
 * 旧 URL やホームの機能カードから、機能ごとの新しい画面へ案内します。
 * スポット・言語・為替の実処理は、それぞれ spot / language / currency パッケージが担当します。
 */
@Controller
public class FeatureController {

    @GetMapping("/features/spots")
    public String spots() {
        // メモ: 旧ホーム画面のリンクも新しいスポット機能へつなぎます。
        return "redirect:/spots";
    }

    @GetMapping("/features/spots/{id}")
    public String spotDetail(@PathVariable Integer id) {
        // メモ: 旧URLを維持しつつ、新しい詳細画面へIDを引き継ぎます。
        return "redirect:/spots/" + id;
    }

    // メモ: マイページは既存セッションの利用者情報だけを表示します。
    @GetMapping("/mypage")
    public String myPage(HttpSession session, Model model) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }
        model.addAttribute("username", session.getAttribute("username"));
        model.addAttribute("userEmail", session.getAttribute("userEmail"));
        return "mypage";
    }

    @GetMapping("/features/{feature}")
    public String feature(@PathVariable String feature, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }

        // メモ: 旧機能カードの識別子を、各機能の新しいURLに集約します。
        return switch (feature) {
            case "receipts" -> "redirect:/receipts/scan";
            case "languages" -> "redirect:/language";
            case "settings" -> "redirect:/mypage";
            case "realtime" -> "redirect:/currency";
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
    }
}
