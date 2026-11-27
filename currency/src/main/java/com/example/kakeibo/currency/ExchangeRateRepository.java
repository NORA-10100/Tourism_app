package com.example.kakeibo.currency;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * メモ: 通貨コードによる更新対象の検索と、画面表示用の一覧取得を行います。
 */
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Integer> {

    Optional<ExchangeRate> findByCurrencyCode(String currencyCode);

    List<ExchangeRate> findAllByOrderByCurrencyCodeAsc();
}
