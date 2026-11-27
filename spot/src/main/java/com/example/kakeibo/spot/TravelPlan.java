package com.example.kakeibo.spot;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 旅行プランテーブル（travel_plans）。誰が何日分のプランを作ったかを保持します。
 */
@Entity
@Table(name = "travel_plans")
public class TravelPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(nullable = false)
    private Integer days;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected TravelPlan() {
    }

    // メモ: 作成者と日数を記録し、後から本人のプランだけを読み出せるようにします。
    public TravelPlan(Integer userId, Integer days) {
        this.userId = userId;
        this.days = days;
        this.createdAt = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public Integer getUserId() {
        return userId;
    }

    public Integer getDays() {
        return days;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
