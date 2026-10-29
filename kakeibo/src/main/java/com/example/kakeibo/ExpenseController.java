package com.example.kakeibo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        return "expenses/List";
    }

    // 新規入力フォーム
    @GetMapping("/new")
    public String form(Model model) {
        Expense expense = new Expense();
        expense.setUserId(DEV_USER_ID);
        expense.setExpenseDate(LocalDate.now());
        model.addAttribute("expense", expense);
        model.addAttribute("categories", CATEGORIES);
        return "expenses/Form";
    }

    // 編集フォーム
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Integer id, Model model) {
        Expense expense = repository.findById(id).orElseThrow();
        model.addAttribute("expense", expense);
        model.addAttribute("categories", CATEGORIES);
        return "expenses/Form";
    }

    // 保存（id が空なら新規登録、id があれば更新）
    @PostMapping
    public String save(@Valid @ModelAttribute("expense") Expense expense,
                       BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("categories", CATEGORIES);
            return "expenses/Form";
        }
        repository.save(expense);
        return "redirect:/expenses/graph";
    }

    // 支出グラフ画面（日別・カテゴリ別の合計、合計金額・外貨換算を表示する）
    @GetMapping("/graph")
    public String graph(Model model) {
        List<Expense> expenses = repository.findByUserIdOrderByExpenseDateDesc(DEV_USER_ID);

        Map<LocalDate, BigDecimal> dailyTotals = new LinkedHashMap<>();
        Map<String, BigDecimal> categoryTotals = new LinkedHashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Expense expense : expenses) {
            BigDecimal amount = expense.getAmountJpy() == null ? BigDecimal.ZERO : expense.getAmountJpy();
            LocalDate expenseDate = expense.getExpenseDate();
            if (expenseDate != null) {
                dailyTotals.merge(expenseDate, amount, BigDecimal::add);
            }
            categoryTotals.merge(expense.getCategory(), amount, BigDecimal::add);
            totalAmount = totalAmount.add(amount);
        }

        List<String> dailyLabels = new ArrayList<>();
        List<Double> dailyValues = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : dailyTotals.entrySet()) {
            dailyLabels.add(entry.getKey().toString());
            dailyValues.add(entry.getValue().doubleValue());
        }

        List<String> categoryLabels = new ArrayList<>();
        List<Double> categoryValues = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : categoryTotals.entrySet()) {
            categoryLabels.add(entry.getKey());
            categoryValues.add(entry.getValue().doubleValue());
        }

        model.addAttribute("expenses", expenses);
        model.addAttribute("dailyLabels", dailyLabels);
        model.addAttribute("dailyValues", dailyValues);
        model.addAttribute("categoryLabels", categoryLabels);
        model.addAttribute("categoryValues", categoryValues);
        model.addAttribute("totalAmount", totalAmount);

        return "expenses/Graph";
    }
}