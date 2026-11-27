package com.example.kakeibo.controller;

import java.util.Locale;
import java.util.Optional;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.kakeibo.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
// メモ: 登録・ログイン・ログアウトとホーム画面への移動を受け持ちます。
public class AuthController {

    // メモ: 登録やログインの実際の処理を依頼するサービスです。
    private final UserService userService;
    private final MessageSource messages;

    // メモ: Spring から利用者サービスを受け取ります。
    public AuthController(UserService userService, MessageSource messages) {
        this.userService = userService;
        this.messages = messages;
    }

    // スプラッシュ画面
    @GetMapping("/")
    // メモ: アプリを開いたとき最初に表示する画面です。
    public String splash() {
        return "splash";
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
            message("login.error")
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
                model.addAttribute("error", registrationError(error));
                return "register";
            }

            redirectAttributes.addFlashAttribute(
                "message",
                message("register.success")
            );

            return "redirect:/login";

        } catch (org.springframework.dao.DuplicateKeyException e) {
            model.addAttribute("error", message("register.error.email_exists"));
            return "register";
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            model.addAttribute("error", message("register.error.email_exists"));
            return "register";
        } catch (org.springframework.dao.DataAccessException e) {
            model.addAttribute("error", message("register.error.database"));
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

    private String registrationError(String error) {
        // メモ: サービスの入力検証結果を翻訳キーへ対応付け、選択中の言語で表示します。
        String key = switch (error) {
            case "ユーザー名は1～15文字で入力してください。" -> "register.error.username";
            case "メールアドレスの形式を確認してください。" -> "register.error.email_format";
            case "パスワードは8～16文字で、大文字・小文字・数字を含めてください。"
                    -> "register.error.password";
            case "確認用パスワードが一致しません。" -> "register.error.password_mismatch";
            case "メールアドレスは255文字以内で入力してください。"
                    -> "register.error.email_length";
            case "このメールアドレスはすでに登録されています。"
                    -> "register.error.email_exists";
            default -> null;
        };
        return key == null ? error : message(key);
    }

    private String message(String key) {
        // メモ: LocaleResolver がセッションから決めた言語でメッセージを取得します。
        Locale locale = LocaleContextHolder.getLocale();
        return messages.getMessage(key, null, locale);
    }
}
