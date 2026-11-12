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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * 家計簿（支出）の画面。基本設計書 2-2-1「支出確認・編集画面」に相当する部分。
 * 編集画面から、誤って重複登録した支出を削除できます。
 */
@Controller
@RequestMapping("/expenses")
// メモ: 支出画面の表示、登録、編集、集計を受け持ちます。
public class ExpenseController {

    // メモ: セッションからログイン中の利用者番号を読み取ります。
    private String userId(HttpSession session) {
        return (String) session.getAttribute("expenseUserId");
    }

    @InitBinder("expense")
    // メモ: 画面から利用者番号や登録日時を書き換えられないようにします。
    public void bindExpense(WebDataBinder binder) {
        binder.setDisallowedFields("userId", "createdAt");
    }

    @ModelAttribute("expense")
    // メモ: 入力画面用の空の支出を作り、利用者番号をセッションから設定します。
    public Expense expense(HttpSession session) {
        Expense expense = new Expense();
        expense.setUserId(userId(session));
        return expense;
    }

    // メモ: 支出がログイン中の利用者のものか調べ、違えば404を返します。
    private Expense ownedExpense(Integer id, HttpSession session) {
        return repository.findByIdAndUserId(id, userId(session))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // 設計書：食事/交通/宿泊/観光/その他
    public static final List<String> CATEGORIES = List.of("食事", "交通", "宿泊", "観光", "その他");

    // メモ: 支出の検索・保存に使う部品です。
    private final ExpenseRepository repository;

    // メモ: Spring から支出用リポジトリを受け取ります。
    public ExpenseController(ExpenseRepository repository) {
        this.repository = repository;
    }

    // 一覧
    @GetMapping
    // メモ: ログイン中の利用者だけの支出一覧を表示します。
    public String list(Model model, HttpSession session) {
        model.addAttribute("expenses", repository.findByUserIdOrderByExpenseDateDesc(userId(session)));
        return "expenses/List";
    }

    // メモ: レシート画像の撮影・選択画面を表示します。
    @GetMapping("/capture")
    public String capture(Model model) {
        model.addAttribute("categories", CATEGORIES);
        return "receipts/scan";
    }

    // メモ: 古い画面からの画像送信は、支出入力画面へ案内します。
    @PostMapping("/capture")
    public String receiveReceipt(@RequestParam("receipt") MultipartFile receipt,
                                 RedirectAttributes redirectAttributes) {
        if (receipt.isEmpty() || receipt.getContentType() == null
                || !receipt.getContentType().startsWith("image/")) {
            redirectAttributes.addFlashAttribute("receiptError", "画像ファイルを選択してください。");
            return "redirect:/expenses/capture";
        }
        redirectAttributes.addFlashAttribute("receiptMessage", "画像を受け付けました。内容を入力してください。");
        return "redirect:/expenses/new";
    }

    // 新規入力フォーム
    @GetMapping("/new")
    // メモ: 今日の日付が入った新規支出フォームを表示します。
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
    // メモ: 所有者を確認してから既存の支出をフォームに表示します。
    public String edit(@PathVariable Integer id, Model model, HttpSession session) {
        Expense expense = ownedExpense(id, session);
        model.addAttribute("expense", expense);
        model.addAttribute("categories", CATEGORIES);
        return "expenses/Form";
    }

    // 保存（id が空なら新規登録、id があれば更新）
    @PostMapping
    // メモ: 入力を検査し、新規登録または所有者確認後の更新を行います。
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
        return "redirect:/expenses/graph";
    }

    // 支出グラフ画面（日別・カテゴリ別の合計、合計金額・外貨換算を表示する）
    @GetMapping("/graph")
    // メモ: 利用者の支出を日別・カテゴリ別に合計し、グラフ画面へ渡します。
    public String graph(Model model, HttpSession session) {
        List<Expense> expenses = repository.findByUserIdOrderByExpenseDateDesc(userId(session));

        Map<LocalDate, BigDecimal> dailyTotals = new LinkedHashMap<>();
        Map<String, BigDecimal> categoryTotals = new LinkedHashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // メモ: 支出を1件ずつ読み、日別・カテゴリ別・全体の金額を足します。
        for (Expense expense : expenses) {
            BigDecimal amount = expense.getAmountJpy() == null ? BigDecimal.ZERO : expense.getAmountJpy();
            LocalDate expenseDate = expense.getExpenseDate();
            if (expenseDate != null) {
                BigDecimal dailyTotal = dailyTotals.get(expenseDate);
                dailyTotals.put(expenseDate, (dailyTotal == null ? BigDecimal.ZERO : dailyTotal).add(amount));
            }
            String category = expense.getCategory();
            BigDecimal categoryTotal = categoryTotals.get(category);
            categoryTotals.put(category, (categoryTotal == null ? BigDecimal.ZERO : categoryTotal).add(amount));
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

    // 支出の削除（重複登録した支出を編集画面から削除できるようにする）
    @PostMapping("/{id}/delete")
    // メモ: 所有者を確認してから削除し、一覧画面へ戻ります。
    public String delete(@PathVariable Integer id, HttpSession session,
                         RedirectAttributes redirectAttributes) {
        repository.delete(ownedExpense(id, session));
        redirectAttributes.addFlashAttribute("message", "支出を削除しました。");
        return "redirect:/expenses";
    }
}
