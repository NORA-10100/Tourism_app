package com.example.kakeibo.spot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * メモ: 旅行プランの表示順に合わせ、日番号と訪問順で明細を取得します。
 */
public interface PlanSpotRepository extends JpaRepository<PlanSpot, Integer> {

    List<PlanSpot> findByPlanIdOrderByDayNumberAscVisitOrderAsc(Integer planId);
}
