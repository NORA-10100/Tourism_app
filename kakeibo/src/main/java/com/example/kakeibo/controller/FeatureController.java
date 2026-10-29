
package com.example.kakeibo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;

@Controller
public class FeatureController {

    @GetMapping("/features/{feature}")
    public String feature(
            @PathVariable String feature,
            HttpSession session,
            Model model) {

        // ログイン状態の確認
        if (!Boolean.TRUE.equals(
                session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }

        if ("receipts".equals(feature)) {
            return "redirect:/expenses";
        }

        // 選択された機能のタイトル
        String title = switch (feature) {

            case "spots" -> "AIスポット案内";

            case "receipts" -> "レシート家計簿";

            case "languages" -> "多言語対応";

            case "realtime" -> "リアルタイム";

            case "settings" -> "設定";

            default -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "その機能は存在しません");
        };

        model.addAttribute("featureTitle", title);

        return "feature";
    }
}
