package com.example.kakeibo.spot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * プラン明細テーブル（plan_spots）。旅行プランに含まれるスポットと、
 * 日ごとの訪問順序・滞在時間を保持します。
 */
@Entity
@Table(name = "plan_spots")
public class PlanSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "plan_id", nullable = false)
    private Integer planId;

    @Column(name = "spot_id", nullable = false)
    private Integer spotId;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(name = "visit_order", nullable = false)
    private Integer visitOrder;

    @Column(name = "stay_minutes")
    private Integer stayMinutes;

    protected PlanSpot() {
    }

    public PlanSpot(Integer planId, Integer spotId, Integer dayNumber,
            Integer visitOrder, Integer stayMinutes) {
        this.planId = planId;
        this.spotId = spotId;
        this.dayNumber = dayNumber;
        this.visitOrder = visitOrder;
        this.stayMinutes = stayMinutes;
    }

    public Integer getId() {
        return id;
    }

    public Integer getPlanId() {
        return planId;
    }

    public Integer getSpotId() {
        return spotId;
    }

    public Integer getDayNumber() {
        return dayNumber;
    }

    public Integer getVisitOrder() {
        return visitOrder;
    }

    public Integer getStayMinutes() {
        return stayMinutes;
    }
}
