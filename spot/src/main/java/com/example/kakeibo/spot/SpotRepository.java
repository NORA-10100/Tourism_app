package com.example.kakeibo.spot;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * メモ: 削除フラグが立っていないスポットと、ID指定の有効スポットを取得します。
 */
public interface SpotRepository extends JpaRepository<Spot, Integer> {

    List<Spot> findByDeleteFlagOrderByCrowdScoreAscReviewScoreDesc(Integer deleteFlag);

    Optional<Spot> findByIdAndDeleteFlag(Integer id, Integer deleteFlag);
}
