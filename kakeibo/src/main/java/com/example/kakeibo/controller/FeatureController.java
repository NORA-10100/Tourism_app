package com.example.kakeibo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;

@Controller
// メモ: 設計書の機能カードから開く画面を案内します。
public class FeatureController {

    // メモ: 現在は画面遷移を体験できるよう、見本のスポットを表示します。
    @GetMapping("/features/spots")
    public String spots(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }
        return "spots";
    }

    // メモ: 一覧から選んだ見本スポットの説明を表示します。
    @GetMapping("/features/spots/{id}")
    public String spotDetail(@PathVariable String id, HttpSession session, Model model) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }
        if (!"1".equals(id) && !"2".equals(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("spotName", "1".equals(id) ? "国際通りの隠れ家カフェ" : "瀬長島ウミカジテラス");
        model.addAttribute("spotArea", "1".equals(id) ? "那覇・国際通り" : "豊見城・瀬長島");
        model.addAttribute("spotDescription", "地元の人にも人気の、沖縄らしい景色と食事を楽しめるスポットです。");
        return "spot-detail";
    }

    // メモ: マイページにログイン中の利用者情報を渡します。
    @GetMapping("/mypage")
    public String myPage(HttpSession session, Model model) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }
        model.addAttribute("username", session.getAttribute("username"));
        model.addAttribute("userEmail", session.getAttribute("userEmail"));
        return "mypage";
    }

    // メモ: 支出入力と言語設定は、実装済みの画面へ直接案内します。
    @GetMapping("/features/{feature}")
    public String feature(@PathVariable String feature, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }

        if ("receipts".equals(feature)) {
            return "redirect:/expenses/capture";
        }
        if ("languages".equals(feature) || "settings".equals(feature)) {
            return "redirect:/mypage";
        }
        if ("realtime".equals(feature)) {
            return "redirect:/currency";
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
