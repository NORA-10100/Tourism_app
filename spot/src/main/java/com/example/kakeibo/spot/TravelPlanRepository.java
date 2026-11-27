package com.example.kakeibo.spot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * メモ: 利用者ごとの旅行プランを新しい順で表示するための検索を提供します。
 */
public interface TravelPlanRepository extends JpaRepository<TravelPlan, Integer> {

    List<TravelPlan> findByUserIdOrderByCreatedAtDesc(Integer userId);
}
