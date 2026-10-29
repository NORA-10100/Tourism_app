package com.example.kakeibo;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * レシート読み取り（OCR）と、読み取った支出の登録。
 * 基本設計書 2-1-1「レシート撮影（OCR）」／2-2-1「支出確認・編集画面」に対応。
 */
@Controller
public class ReceiptController {

    private final ReceiptOcrService ocrService;
    private final ExpenseRepository repository;

    public ReceiptController(ReceiptOcrService ocrService, ExpenseRepository repository) {
        this.ocrService = ocrService;
        this.repository = repository;
    }

    // レシート撮影・アップロード画面
    @GetMapping("/receipts/scan")
    public String scan(Model model) {
        model.addAttribute("categories", ExpenseController.CATEGORIES);
        return "receipts/scan";
    }

    // 画像を読み取って、金額などを JSON で返す（画面から fetch で呼ぶ）
    @PostMapping("/api/receipts/scan")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> scanApi(
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "レシート画像を選択してください。"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "画像ファイル（JPEG / PNG など）を選択してください。"));
        }

        try {
            ReceiptOcrService.ReceiptData data =
                    ocrService.read(file.getBytes(), contentType);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("itemName", data.itemName());
            body.put("amount", data.amount());
            body.put("date", data.date().toString());
            body.put("category", data.category());
            body.put("currency", data.currency());
            return ResponseEntity.ok(body);

        } catch (ReceiptOcrService.ReceiptOcrException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.getMessage());
            if (e.getDetail() != null && !e.getDetail().isBlank()) {
                body.put("detail", e.getDetail());
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "画像ファイルを読み込めませんでした。"));
        }
    }

    // 読み取り結果を確認して支出を登録する
    @PostMapping("/receipts/save")
    public String save(
            @RequestParam String itemName,
            @RequestParam BigDecimal amountJpy,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expenseDate,
            @RequestParam String category,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) BigDecimal originalAmount,
            RedirectAttributes redirectAttributes) {

        if (itemName == null || itemName.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "品目名を入力してください。");
            return "redirect:/receipts/scan";
        }
        if (amountJpy == null || amountJpy.signum() <= 0) {
            redirectAttributes.addFlashAttribute("error", "金額は0より大きい数値を入力してください。");
            return "redirect:/receipts/scan";
        }

        Expense expense = new Expense();
        expense.setUserId(ExpenseController.DEV_USER_ID);
        expense.setItemName(itemName.trim());
        expense.setAmountJpy(amountJpy);
        expense.setExpenseDate(expenseDate);
        expense.setCategory(category);

        // 外貨のレシートは元通貨・元金額も残す（円換算は為替機能側の担当）
        if (currency != null && !currency.isBlank() && !"JPY".equalsIgnoreCase(currency)) {
            expense.setOriginalCurrency(currency.trim().toUpperCase());
            expense.setOriginalAmount(originalAmount != null ? originalAmount : amountJpy);
        }

        repository.save(expense);

        redirectAttributes.addFlashAttribute("message", "レシートから支出を登録しました。");
        return "redirect:/expenses";
    }
}
