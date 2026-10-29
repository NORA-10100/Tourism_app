package com.example.kakeibo;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    // ① スプラッシュ画面
    @GetMapping("/")
    public String splash() {
        return "splash";
    }

    // ② 言語選択画面
    @GetMapping("/language")
    public String language() {
        return "language";
    }

    // 言語を確定
    @PostMapping("/language")
    public String selectLanguage(
            @RequestParam String language,
            HttpSession session) {

        session.setAttribute("language", language);

        return "redirect:/login";
    }

    // ③ ログイン画面
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // ログイン処理（仮実装）
    @PostMapping("/login")
    public String doLogin(
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            Model model) {

        // 現段階では仮の認証
        if ("test@example.com".equals(email)
                && "Test1234".equals(password)) {

            session.setAttribute("loggedIn", true);

            // ホーム画面は今回作らない
            return "redirect:/login/success";
        }

        model.addAttribute(
                "error",
                "メールアドレスまたはパスワードが違います。");

        return "login";
    }

    // 仮の成功画面（ホーム画面ではありません）
    @GetMapping("/login/success")
    public String loginSuccess(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }

        return "login-success";
    }

    // ④ 新規登録画面
    @GetMapping("/register")
    public String register() {
        return "register";
    }

    // 新規登録処理（仮実装：登録して即ログイン扱いにする）
    @PostMapping("/register")
    public String doRegister(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session) {

        // 現段階では仮の登録（セッションにログイン状態を持たせる）
        session.setAttribute("loggedIn", true);
        session.setAttribute("username", username);

        return "redirect:/login/success";
    }

    // ⑤ ログアウト確認画面
    @GetMapping("/logout/confirm")
    public String logoutConfirm(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }

        return "logout";
    }

    // ログアウト実行
    @PostMapping("/logout")
    public String doLogout(HttpSession session) {
        session.invalidate();

        return "redirect:/language";
    }
}
