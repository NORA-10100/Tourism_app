package com.example.kakeibo.currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 為替レートテーブル（exchange_rates）。外部 API から取得したレートを
 * キャッシュし、API 取得失敗時のフォールバック表示に使います。
 */
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "currency_code", nullable = false, unique = true, length = 3)
    private String currencyCode;

    @Column(name = "rate_to_jpy", nullable = false, precision = 10, scale = 4)
    private BigDecimal rateToJpy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected ExchangeRate() {
    }

    // メモ: 新規登録時も更新時も、値と取得時刻を同じ処理で設定します。
    public ExchangeRate(String currencyCode, BigDecimal rateToJpy) {
        this.currencyCode = currencyCode;
        setRate(rateToJpy);
    }

    // メモ: updated_at を最新値に揃え、古いレートとの見分けに使います。
    public void update(BigDecimal rateToJpy) {
        setRate(rateToJpy);
    }

    private void setRate(BigDecimal rateToJpy) {
        this.rateToJpy = rateToJpy;
        this.updatedAt = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getRateToJpy() {
        return rateToJpy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
