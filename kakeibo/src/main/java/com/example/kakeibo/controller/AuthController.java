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
// メモ: 登録・ログイン・ログアウトとホーム画面への移動を受け持ちます。
public class AuthController {

    // メモ: 登録やログインの実際の処理を依頼するサービスです。
    private final UserService userService;

    // メモ: Spring から利用者サービスを受け取ります。
    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // スプラッシュ画面
    @GetMapping("/")
    // メモ: アプリを開いたとき最初に表示する画面です。
    public String splash() {
        return "splash";
    }

    // 言語選択画面
    @GetMapping("/language")
    // メモ: 利用者に表示言語を選んでもらう画面です。
    public String language(HttpSession session, Model model) {
        model.addAttribute("selectedLanguage", session.getAttribute("language"));
        model.addAttribute("loggedIn", Boolean.TRUE.equals(session.getAttribute("loggedIn")));
        return "language";
    }

    @PostMapping("/language")
    // メモ: 選ばれた言語をセッションに保存してログイン画面へ進みます。
    public String selectLanguage(
            @RequestParam String language,
            HttpSession session) {

        if (!java.util.Set.of("ja", "en", "zh-CN", "zh-TW", "ko").contains(language)) {
            return "redirect:/language";
        }
        session.setAttribute("language", language);
        return Boolean.TRUE.equals(session.getAttribute("loggedIn"))
                ? "redirect:/home"
                : "redirect:/login";
    }

    // ログイン画面
    @GetMapping("/login")
    // メモ: ログイン入力画面を表示します。
    public String login() {
        return "login";
    }

    // DBを使ったログイン処理
    @PostMapping("/login")
    // メモ: 認証に成功したら利用者情報をセッションに保存します。
    public String doLogin(
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            HttpServletRequest request,
            Model model) {

        Optional<String> username =
                userService.login(email, password);

        // メモ: ログイン成功時は古いセッションを作り直してから利用者情報を記録します。
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
    // メモ: ログイン済みの利用者だけに確認画面を表示します。
    public String loginSuccess(HttpSession session, Model model) {

        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }

        model.addAttribute("username", session.getAttribute("username"));

        return "login-success";
    }

    // 新規登録画面
    @GetMapping("/register")
    // メモ: アカウント登録フォームを表示します。
    public String register() {
        return "register";
    }

    // DBへの新規登録
    @PostMapping("/register")
    // メモ: 登録内容を検査し、成功・入力エラー・DBエラーを画面に返します。
    public String doRegister(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model,
            RedirectAttributes redirectAttributes) {

        // メモ: 登録サービスの戻り値がエラーメッセージなら入力画面に戻します。
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
    // メモ: ログイン済みの利用者にログアウト確認画面を表示します。
    public String logoutConfirm(HttpSession session) {

        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }

        return "logout";
    }

    // ログアウト
    @PostMapping("/logout")
    // メモ: セッションを破棄して言語選択画面へ戻します。
    public String doLogout(HttpSession session) {
        session.invalidate();
        return "redirect:/language";
    }


    // ログイン後のホーム画面
    @GetMapping("/home")
    // メモ: ログイン済みの利用者名をホーム画面へ渡します。
    public String home(HttpSession session, Model model) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            return "redirect:/login";
        }
        model.addAttribute("username", session.getAttribute("username"));
        return "home";
    }
}
