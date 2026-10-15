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

/**
 * 家計簿（支出）の画面。基本設計書 2-2-1「支出確認・編集画面」に相当する部分。
 * 設計書の権限は SELECT/INSERT/UPDATE のみなので、削除機能は作っていない。
 */
@Controller
@RequestMapping("/expenses")
public class ExpenseController {

    // ログイン機能ができるまでの仮の端末ID（設計書：端末識別ID・匿名）
    private static final String DEV_USER_ID = "dev-device-001";

    // 設計書：食事/交通/宿泊/観光/その他
    private static final List<String> CATEGORIES = List.of("食事", "交通", "宿泊", "観光", "その他");

    private final ExpenseRepository repository;

    public ExpenseController(ExpenseRepository repository) {
        this.repository = repository;
    }

    // 一覧
    @GetMapping
    public String list(Model model) {
        model.addAttribute("expenses", repository.findByUserIdOrderByExpenseDateDesc(DEV_USER_ID));
        return "expenses/list";
    }

    // 新規入力フォーム
    @GetMapping("/new")
    public String form(Model model) {
        Expense expense = new Expense();
        expense.setUserId(DEV_USER_ID);
        expense.setExpenseDate(LocalDate.now());
        model.addAttribute("expense", expense);
        model.addAttribute("categories", CATEGORIES);
        return "expenses/form";
    }

    // 編集フォーム
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Integer id, Model model) {
        Expense expense = repository.findById(id).orElseThrow();
        model.addAttribute("expense", expense);
        model.addAttribute("categories", CATEGORIES);
        return "expenses/form";
    }

    // 保存（id が空なら新規登録、id があれば更新）
    @PostMapping
    public String save(@Valid @ModelAttribute("expense") Expense expense,
                       BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("categories", CATEGORIES);
            return "expenses/form";
        }
        repository.save(expense);
        return "redirect:/expenses";
    }
}