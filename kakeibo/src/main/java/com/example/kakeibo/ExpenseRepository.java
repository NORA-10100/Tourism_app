package com.example.kakeibo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Integer> {
    java.util.Optional<Expense> findByIdAndUserId(Integer id, String userId);

    // 自分の支出を、支出日の新しい順に取得する
    List<Expense> findByUserIdOrderByExpenseDateDesc(String userId);
}