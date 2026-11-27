package com.example.kakeibo.spot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * スポットテーブル（spots）。基本設計書 5-1 (2) b に対応します。
 * name は {@code {"ja":"...","en":"..."}} 形式の多言語 JSON です。
 */
@Entity
@Table(name = "spots")
public class Spot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 400)
    private String name;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 50)
    private String area;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "business_hours", length = 100)
    private String businessHours;

    @Column(name = "access_info", length = 200)
    private String accessInfo;

    @Column(name = "crowd_score")
    private Integer crowdScore;

    @Column(name = "review_score", precision = 3, scale = 1)
    private BigDecimal reviewScore;

    @Column(name = "delete_flag")
    private Integer deleteFlag;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected Spot() {
    }

    public Spot(String name, String category, String area,
            BigDecimal latitude, BigDecimal longitude,
            String businessHours, String accessInfo,
            Integer crowdScore, BigDecimal reviewScore) {
        this.name = name;
        this.category = category;
        this.area = area;
        this.latitude = latitude;
        this.longitude = longitude;
        this.businessHours = businessHours;
        this.accessInfo = accessInfo;
        this.crowdScore = crowdScore;
        this.reviewScore = reviewScore;
        this.deleteFlag = 0;
        this.createdAt = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getArea() {
        return area;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getBusinessHours() {
        return businessHours;
    }

    public String getAccessInfo() {
        return accessInfo;
    }

    public Integer getCrowdScore() {
        return crowdScore;
    }

    public BigDecimal getReviewScore() {
        return reviewScore;
    }

    public Integer getDeleteFlag() {
        return deleteFlag;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
