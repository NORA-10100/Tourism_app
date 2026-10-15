package com.example.kakeibo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 支出テーブル（expenses）
 * 基本設計書 5-1 (2) e 支出テーブル に対応
 */
@Entity
@Table(name = "expenses")
public class Expense {

    // 設計書は INTEGER。Long にすると ddl-auto=validate でエラーになるので Integer
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // 端末識別ID（匿名）VARCHAR(64)
    @NotBlank
    @Size(max = 64)
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    // 品目名 VARCHAR(50)（メッセージは設計書 4-3 のとおり）
    @NotBlank(message = "品目名を入力してください。")
    @Size(max = 50, message = "品目名は50文字以内で入力してください。")
    @Column(name = "item_name", nullable = false, length = 50)
    private String itemName;

    // 金額（円）DECIMAL(10,2)
    @NotNull(message = "金額は数字のみで入力してください。")
    @Column(name = "amount_jpy", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountJpy;

    // カテゴリ VARCHAR(30)：食事/交通/宿泊/観光/その他
    @NotBlank(message = "カテゴリを選択してください。")
    @Size(max = 30)
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    // 支出日 DATE
    @NotNull(message = "支出日を入力してください。")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    // 元通貨 VARCHAR(3) DEFAULT 'JPY'（null を送るとDBのDEFAULTが効かないのでJava側でも初期値を持つ）
    @Column(name = "original_currency", length = 3)
    private String originalCurrency = "JPY";

    // 元金額 DECIMAL(10,2)（外貨のときだけ）
    @Column(name = "original_amount", precision = 10, scale = 2)
    private BigDecimal originalAmount;

    // 登録日時 DATETIME DEFAULT NOW()：DB側に任せるので、Javaからは書き込まない
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public BigDecimal getAmountJpy() { return amountJpy; }
    public void setAmountJpy(BigDecimal amountJpy) { this.amountJpy = amountJpy; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }

    public String getOriginalCurrency() { return originalCurrency; }
    public void setOriginalCurrency(String originalCurrency) { this.originalCurrency = originalCurrency; }

    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
//test
