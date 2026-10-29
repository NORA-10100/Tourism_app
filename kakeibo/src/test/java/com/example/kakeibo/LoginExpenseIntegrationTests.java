package com.example.kakeibo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
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
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class LoginExpenseIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ExpenseRepository expenses;
    @Autowired UserRepository users;

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

    @Test
    void registrationLoginAndExpensesAreIntegratedAndIsolated() throws Exception {
        mvc.perform(get("/expenses")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/home")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        MockHttpSession alice = registerAndLogin("alice@example.com");
        mvc.perform(get("/home").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("旅行者")));
        mvc.perform(get("/features/receipts").session(alice)).andExpect(redirectedUrl("/expenses"));
        mvc.perform(post("/expenses").session(alice).with(csrf())
                .param("userId", "forged-user").param("itemName", "ソーキそば")
                .param("amountJpy", "1200").param("category", "食事")
                .param("expenseDate", "2026-10-29"))
                .andExpect(redirectedUrl("/expenses"));
        var expense = expenses.findByUserIdOrderByExpenseDateDesc(
                (String) alice.getAttribute("expenseUserId")).get(0);
        assertNotEquals("forged-user", expense.getUserId());
        mvc.perform(get("/expenses").session(alice)).andExpect(status().isOk())
                .andExpect(content().string(containsString("ソーキそば")));
        mvc.perform(get("/expenses/" + expense.getId() + "/edit").session(alice))
                .andExpect(status().isOk());
        MockHttpSession bob = registerAndLogin("bob@example.com");
        mvc.perform(get("/expenses").session(bob)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("ソーキそば"))));
        mvc.perform(get("/expenses/" + expense.getId() + "/edit").session(bob))
                .andExpect(status().isNotFound());
        mvc.perform(post("/expenses").session(bob).with(csrf())
                .param("id", expense.getId().toString()).param("itemName", "改ざん")
                .param("amountJpy", "1").param("category", "食事")
                .param("expenseDate", "2026-10-29"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/logout").session(alice).with(csrf()))
                .andExpect(redirectedUrl("/language"));
        assertTrue(alice.isInvalid());
        mvc.perform(get("/expenses")).andExpect(redirectedUrl("/login"));
    }

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
    }
}
