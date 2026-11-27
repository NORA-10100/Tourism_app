package com.example.kakeibo.language;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.kakeibo.common.SessionUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 言語選択・言語設定画面（画面ID 0-2-1 / 3-1-1）を受け持ちます。
 */
@Controller
public class LanguageController {

    @GetMapping("/language")
    public String settings(HttpServletRequest request, HttpSession session, Model model) {
        // メモ: 選択済みの言語を優先し、未選択時だけ端末言語をおすすめとして表示します。
        String selected = SessionUser.languageOrNull(session);
        // まだ選んでいない場合は、端末の言語設定をおすすめとして表示します。
        String recommended = selected != null
                ? selected
                : LanguageSupport.fromLocale(request.getLocale());

        model.addAttribute("selectedLanguage", selected);
        model.addAttribute("recommendedLanguage", recommended);
        model.addAttribute("languages", LanguageSupport.OPTIONS);
        return "language/settings";
    }

    @PostMapping("/language")
    public String apply(@RequestParam String language, HttpSession session) {
        // メモ: 不正な言語コードを保存せず、ログイン状態に応じた画面へ戻します。
        if (!LanguageSupport.isSupported(language)) {
            return "redirect:/language";
        }
        session.setAttribute("language", language);
        return SessionUser.isLoggedIn(session) ? "redirect:/home" : "redirect:/login";
    }
}
