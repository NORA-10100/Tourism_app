package com.example.kakeibo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * 家計簿（支出）の画面。基本設計書 2-2-1「支出確認・編集画面」に相当する部分。
 * 設計書の権限は SELECT/INSERT/UPDATE のみなので、削除機能は作っていない。
 */
@Controller
@RequestMapping("/expenses")
public class ExpenseController {

    private String userId(HttpSession session) {
        return (String) session.getAttribute("expenseUserId");
    }

    @InitBinder("expense")
    public void bindExpense(WebDataBinder binder) {
        binder.setDisallowedFields("userId", "createdAt");
    }

    @ModelAttribute("expense")
    public Expense expense(HttpSession session) {
        Expense expense = new Expense();
        expense.setUserId(userId(session));
        return expense;
    }

    private Expense ownedExpense(Integer id, HttpSession session) {
        return repository.findByIdAndUserId(id, userId(session))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // 設計書：食事/交通/宿泊/観光/その他
    private static final List<String> CATEGORIES = List.of("食事", "交通", "宿泊", "観光", "その他");

    private final ExpenseRepository repository;

    public ExpenseController(ExpenseRepository repository) {
        this.repository = repository;
    }

    // 一覧
    @GetMapping
    public String list(Model model, HttpSession session) {
        model.addAttribute("expenses", repository.findByUserIdOrderByExpenseDateDesc(userId(session)));
        return "expenses/List";
    }

    // 新規入力フォーム
    @GetMapping("/new")
    public String form(Model model, HttpSession session) {
        Expense expense = new Expense();
        expense.setUserId(userId(session));
        expense.setExpenseDate(LocalDate.now());
        model.addAttribute("expense", expense);
        model.addAttribute("categories", CATEGORIES);
        return "expenses/Form";
    }

    // 編集フォーム
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Integer id, Model model, HttpSession session) {
        Expense expense = ownedExpense(id, session);
        model.addAttribute("expense", expense);
        model.addAttribute("categories", CATEGORIES);
        return "expenses/Form";
    }

    // 保存（id が空なら新規登録、id があれば更新）
    @PostMapping
    public String save(@Valid @ModelAttribute("expense") Expense expense,
                       BindingResult result, Model model, HttpSession session) {
        if (expense.getId() != null) {
            ownedExpense(expense.getId(), session);
        }
        if (result.hasErrors()) {
            model.addAttribute("categories", CATEGORIES);
            return "expenses/Form";
        }
        repository.save(expense);
        return "redirect:/expenses";
    }
}