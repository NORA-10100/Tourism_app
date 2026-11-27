package com.example.kakeibo.spot;

import java.util.List;

/**
 * AI プラン画面に表示する旅行プランです。
 */
public record TravelPlanView(Integer id, int days, List<PlanDay> dayList) {

    public record PlanDay(int dayNumber, List<PlanEntry> entries) {
    }

    public record PlanEntry(String time, SpotView spot, int stayMinutes, String travelNote) {
    }
}
