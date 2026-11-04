package com.example.kakeibo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

// メモ: 支出データの検索・保存・削除をデータベースに依頼します。
public interface ExpenseRepository extends JpaRepository<Expense, Integer> {
    // メモ: 指定した支出が指定した利用者のものか確認します。
    java.util.Optional<Expense> findByIdAndUserId(Integer id, String userId);

    // メモ: 利用者ごとの支出を日付の新しい順に取得します。
    List<Expense> findByUserIdOrderByExpenseDateDesc(String userId);
}
