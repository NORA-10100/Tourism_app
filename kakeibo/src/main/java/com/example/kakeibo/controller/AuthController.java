package com.example.kakeibo.controller;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.kakeibo.service.UserService;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // スプラッシュ画面
    @GetMapping("/")
    public String splash() {
        return "splash";
    }

    // 言語選択画面
    @GetMapping("/language")
    public String language() {
        return "language";
    }

    @PostMapping("/language")
    public String selectLanguage(
            @RequestParam String language,
            HttpSession session) {

        session.setAttribute("language", language);
        return "redirect:/login";
    }

    // ログイン画面
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // DBを使ったログイン処理
    @PostMapping("/login")
    public String doLogin(
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            HttpServletRequest request,
            Model model) {

        Optional<String> username =
                userService.login(email, password);

        if (username.isPresent()) {
            Object language = session.getAttribute("language");
            session.invalidate();
            session = request.getSession(true);
            if (language != null) {
                session.setAttribute("language", language);
            }
            session.setAttribute("loggedIn", true);
            session.setAttribute("expenseUserId", "user-" + userService.userId(email.trim()));
            session.setAttribute("userEmail", email.trim());
            session.setAttribute("username", username.get());

            return "redirect:/home";
        }

        model.addAttribute(
            "error",
            "メールアドレスもしくはパスワードが間違っています。"
        );

        return "login";
    }

    // ログイン成功後のテスト用画面
    @GetMapping("/login/success")
    public String loginSuccess(HttpSession session) {

        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }

        return "login-success";
    }

    // 新規登録画面
    @GetMapping("/register")
    public String register() {
        return "register";
    }

    // DBへの新規登録
    @PostMapping("/register")
    public String doRegister(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            String error = userService.register(
                username, email, password, confirmPassword
            );

            if (error != null) {
                model.addAttribute("error", error);
                return "register";
            }

            redirectAttributes.addFlashAttribute(
                "message",
                "新規登録が完了しました。ログインしてください。"
            );

            return "redirect:/login";

        } catch (org.springframework.dao.DuplicateKeyException e) {
            model.addAttribute(
                "error",
                "このメールアドレスはすでに登録されています。"
            );
            return "register";
        } catch (org.springframework.dao.DataAccessException e) {
            model.addAttribute(
                "error",
                "データベースへの登録に失敗しました。DB接続とテーブルを確認してください。"
            );
            return "register";
        }
    }

    // ログアウト確認画面
    @GetMapping("/logout/confirm")
    public String logoutConfirm(HttpSession session) {

        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }

        return "logout";
    }

    // ログアウト
    @PostMapping("/logout")
    public String doLogout(HttpSession session) {
        session.invalidate();
        return "redirect:/language";
    }


    // ログイン後のホーム画面
    @GetMapping("/home")
    public String home(HttpSession session, Model model) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }
        model.addAttribute("username", session.getAttribute("username"));
        return "home";
    }
}
