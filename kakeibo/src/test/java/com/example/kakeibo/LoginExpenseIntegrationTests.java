package com.example.kakeibo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import com.example.kakeibo.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:login_integration;MODE=Oracle;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    // メモ: テスト中のネットワーク状態に左右されないよう、為替API呼び出しを無効にします。
    "vita.currency.remote-enabled=false"
})
@AutoConfigureMockMvc
// メモ: テスト用DBを使い、登録から支出操作までをまとめて確認します。
class LoginExpenseIntegrationTests {
    // メモ: HTTPリクエストを画面サーバーへ送るテスト用部品です。
    @Autowired MockMvc mvc;
    // メモ: テストで登録した支出を確認する部品です。
    @Autowired ExpenseRepository expenses;
    // メモ: テストで登録した利用者を確認する部品です。
    @Autowired UserRepository users;

    // メモ: 指定メールアドレスで利用者を作り、ログイン状態を返します。
    private MockHttpSession registerAndLogin(String email) throws Exception {
        mvc.perform(post("/register").with(csrf()).param("username", "旅行者")
                .param("email", email).param("password", "Travel123")
                .param("confirmPassword", "Travel123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        assertNotEquals("Travel123", users.findPasswordHashByEmail(email).orElseThrow());
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("email", email).param("password", "Travel123"))
                .andExpect(redirectedUrl("/home")).andReturn().getRequest().getSession(false);
    }

    // メモ: 登録・ログイン・支出の分離・編集・削除権限を確認します。
    @Test
    void registrationLoginAndExpensesAreIntegratedAndIsolated() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/css/app.css")));
        mvc.perform(get("/expenses")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/home")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("auth-panel")));
        mvc.perform(get("/register")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/css/app.css")))
                .andExpect(content().string(containsString("新規登録")));
        mvc.perform(get("/language")).andExpect(status().isOk())
                .andExpect(content().string(containsString("言語を選択")));
        MockHttpSession alice = registerAndLogin("alice@example.com");
        mvc.perform(get("/home").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("旅行者")));
        mvc.perform(get("/expenses").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("支出一覧")))
                .andExpect(content().string(containsString("href=\"/receipts/scan\"")));
        mvc.perform(get("/features/spots").session(alice))
                .andExpect(redirectedUrl("/spots"));
        mvc.perform(get("/spots").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("条件を入力")));
        mvc.perform(get("/spots/results").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("おすすめスポット")))
                .andExpect(content().string(containsString("openstreetmap.org")));
        mvc.perform(get("/features/spots/1").session(alice))
                .andExpect(redirectedUrl("/spots/1"));
        mvc.perform(get("/spots/1").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("首里城公園")));
        mvc.perform(post("/spots/selection").session(alice).with(csrf())
                .param("spotId", "1")).andExpect(redirectedUrl("/spots/1"));
        mvc.perform(get("/spots/days").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("日数を選択")));
        var planResponse = mvc.perform(post("/spots/plan").session(alice).with(csrf())
                .param("days", "1")).andExpect(status().is3xxRedirection()).andReturn();
        mvc.perform(get(planResponse.getResponse().getRedirectedUrl()).session(alice))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("旅行プラン")));
        mvc.perform(get("/mypage").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("家計簿データ")));
        mvc.perform(get("/currency").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("為替レートと通貨換算")))
                .andExpect(content().string(containsString("id=\"fx-swap\"")))
                .andExpect(content().string(containsString("HKD")));
        mvc.perform(post("/currency/convert").session(alice).with(csrf())
                .param("amount", "100").param("currency", "USD"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("14,932")));
        mvc.perform(post("/currency/convert").session(alice).with(csrf())
                .param("amount", "100").param("from", "USD").param("to", "EUR"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("93.16")));
        mvc.perform(post("/currency/convert").session(alice).with(csrf())
                .param("amount", "-1").param("currency", "USD"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        mvc.perform(post("/currency/convert").session(alice).with(csrf())
                .param("amount", "invalid").param("currency", "USD"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("error"))
                .andExpect(model().attribute("inputAmount", "invalid"));
        mvc.perform(get("/language").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("言語設定")));
        mvc.perform(get("/logout/confirm").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("auth-panel")));
        mvc.perform(post("/language").session(alice).with(csrf()).param("language", "en"))
                .andExpect(redirectedUrl("/home"));
        mvc.perform(get("/expenses/graph").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("支出グラフ")))
                .andExpect(content().string(containsString("Budget")));
        mvc.perform(get("/expenses/capture").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("レシートを撮影する")));
        mvc.perform(get("/receipts/scan").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("camera-btn")))
                .andExpect(content().string(containsString("gallery-btn")))
                .andExpect(content().string(containsString("class=\"app-shell\"")))
                .andExpect(content().string(containsString("class=\"bottom-nav\"")))
                .andExpect(content().string(containsString("手入力で登録")));
        mvc.perform(multipart("/expenses/capture").file(new MockMultipartFile(
                "receipt", "receipt.jpg", "image/jpeg", new byte[] { 1, 2, 3 }))
                .session(alice).with(csrf()))
                .andExpect(redirectedUrl("/expenses/new"));
        mvc.perform(get("/features/receipts").session(alice))
                .andExpect(redirectedUrl("/expenses"));
        mvc.perform(post("/expenses").session(alice).with(csrf())
                .param("userId", "forged-user").param("itemName", "ソーキそば")
                .param("amountJpy", "1200").param("category", "食事")
                .param("expenseDate", "2026-10-29"))
                .andExpect(redirectedUrl("/expenses/graph"));
        var expense = expenses.findByUserIdOrderByExpenseDateDesc(
                (String) alice.getAttribute("expenseUserId")).get(0);
        assertNotEquals("forged-user", expense.getUserId());
        mvc.perform(get("/expenses").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("ソーキそば")));
        mvc.perform(get("/expenses/" + expense.getId() + "/edit").session(alice))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("この支出を削除")));
        MockHttpSession bob = registerAndLogin("bob@example.com");
        mvc.perform(get(planResponse.getResponse().getRedirectedUrl()).session(bob))
                .andExpect(status().isNotFound());
        mvc.perform(get("/expenses").session(bob)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("ソーキそば"))));
        mvc.perform(get("/expenses/" + expense.getId() + "/edit").session(bob))
                .andExpect(status().isNotFound());
        mvc.perform(post("/expenses/" + expense.getId() + "/delete").session(bob).with(csrf()))
                .andExpect(status().isNotFound());
        assertTrue(expenses.findById(expense.getId()).isPresent());
        mvc.perform(post("/expenses").session(bob).with(csrf())
                .param("id", expense.getId().toString()).param("itemName", "改ざん")
                .param("amountJpy", "1").param("category", "食事")
                .param("expenseDate", "2026-10-29"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/expenses/" + expense.getId() + "/delete").session(alice).with(csrf()))
                .andExpect(redirectedUrl("/expenses"));
        assertTrue(expenses.findById(expense.getId()).isEmpty());
        mvc.perform(get("/expenses").session(alice))
                .andExpect(content().string(containsString("支出を削除しました")));
        mvc.perform(post("/logout").session(alice).with(csrf()))
                .andExpect(redirectedUrl("/language"));
        assertTrue(alice.isInvalid());
        mvc.perform(get("/expenses")).andExpect(redirectedUrl("/login"));
    }

    // メモ: ホームの各機能リンク、旧URL転送、分割モジュールの画面資材を通しで確認します。
    @Test
    void featureNavigationAndModuleResourcesAreReachable() throws Exception {
        mvc.perform(get("/spots")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/currency")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/receipts/scan")).andExpect(redirectedUrl("/login"));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loggedIn", true);
        session.setAttribute("expenseUserId", "user-1");
        session.setAttribute("username", "画面遷移テスト");
        session.setAttribute("userEmail", "navigation@example.com");
        session.setAttribute("language", "ja");

        mvc.perform(get("/home").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/spots\"")))
                .andExpect(content().string(containsString("href=\"/expenses\"")))
                .andExpect(content().string(containsString(
                        "<a class=\"nav-item\" href=\"/expenses\"")))
                .andExpect(content().string(containsString("href=\"/currency\"")))
                .andExpect(content().string(containsString("href=\"/language\"")));

        mvc.perform(get("/features/spots").session(session))
                .andExpect(redirectedUrl("/spots"));
        mvc.perform(get("/features/realtime").session(session))
                .andExpect(redirectedUrl("/currency"));
        mvc.perform(get("/features/languages").session(session))
                .andExpect(redirectedUrl("/language"));
        mvc.perform(get("/features/settings").session(session))
                .andExpect(redirectedUrl("/mypage"));
        mvc.perform(get("/features/receipts").session(session))
                .andExpect(redirectedUrl("/expenses"));

        mvc.perform(get("/spots/results").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/spots/1\"")))
                .andExpect(content().string(containsString("href=\"/spots/days\"")));
        mvc.perform(post("/spots/selection").session(session).with(csrf())
                .param("spotId", "1"))
                .andExpect(redirectedUrl("/spots/1"));
        mvc.perform(get("/spots/1").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/spots/days\"")));
        mvc.perform(get("/spots/days").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/spots/plan\"")));

        var planResponse = mvc.perform(post("/spots/plan").session(session).with(csrf())
                .param("days", "1"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        mvc.perform(get(planResponse.getResponse().getRedirectedUrl()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/currency\"")))
                .andExpect(content().string(containsString("href=\"/expenses\"")));

        mvc.perform(get("/currency").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/currency/convert\"")));
        mvc.perform(get("/language").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/language\"")));
        mvc.perform(get("/mypage").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/logout/confirm\"")));

        mvc.perform(get("/receipts/scan").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/css/app.css")))
                .andExpect(content().string(containsString("href=\"/home\"")))
                .andExpect(content().string(containsString("class=\"bottom-nav\"")))
                .andExpect(content().string(containsString("/api/receipts/scan")))
                .andExpect(content().string(containsString("action=\"/receipts/save\"")));
        mvc.perform(get("/expenses/capture").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/receipts/scan")));
        mvc.perform(post("/receipts/save").session(session).with(csrf())
                .param("itemName", "画面遷移テストの支出")
                .param("amountJpy", "850")
                .param("expenseDate", "2026-11-27")
                .param("category", "食事"))
                .andExpect(redirectedUrl("/expenses"));
        mvc.perform(get("/expenses").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("画面遷移テストの支出")));
        mvc.perform(get("/css/receipt.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
        mvc.perform(get("/js/receipt.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("scanUrlMeta")));
    }

    // メモ: 不正な入力とCSRFトークンがない送信を拒否することを確認します。
    @Test
    void rejectsInvalidLoginRegistrationAndMissingCsrf() throws Exception {
        mvc.perform(post("/login").param("email", "none@example.com").param("password", "wrong"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/login").with(csrf()).param("email", "none@example.com")
                .param("password", "wrong")).andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));
        mvc.perform(post("/register").with(csrf()).param("username", "旅行者")
                .param("email", "invalid").param("password", "weak")
                .param("confirmPassword", "weak")).andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));
        assertFalse(users.existsByEmail("invalid"));

        MockHttpSession english = new MockHttpSession();
        english.setAttribute("language", "en");
        mvc.perform(post("/register").session(english).with(csrf())
                .param("username", "").param("email", "invalid")
                .param("password", "weak").param("confirmPassword", "weak"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Enter a user name between 1 and 15 characters.")));
    }
}
