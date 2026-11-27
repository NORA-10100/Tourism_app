package com.example.kakeibo.spot;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * 観光客の少ないローカル・穴場スポットを優先して提案します。
 *
 * <p>混雑スコアが低いほど、口コミスコアが高いほど高評価になる決定論的な式です。
 * 将来、天気・混雑情報の外部 API を組み合わせる場合は、この式に補正値を加えます。</p>
 */
@Service
public class SpotRecommendationService {

    private final SpotRepository spotRepository;
    private final SpotService spotService;

    public SpotRecommendationService(SpotRepository spotRepository, SpotService spotService) {
        this.spotRepository = spotRepository;
        this.spotService = spotService;
    }

    public List<SpotView> recommend(String area, String category, String language) {
        List<Spot> filtered = spotRepository
                .findByDeleteFlagOrderByCrowdScoreAscReviewScoreDesc(0)
                .stream()
                .filter(spot -> matches(spot.getArea(), area))
                .filter(spot -> matches(spot.getCategory(), category))
                .sorted(Comparator.comparingDouble(this::score).reversed())
                .toList();

        return spotService.toViews(filtered, language);
    }

    private static boolean matches(String value, String condition) {
        return condition == null || condition.isBlank() || condition.equals(value);
    }

    private double score(Spot spot) {
        int crowd = spot.getCrowdScore() == null ? 50 : spot.getCrowdScore();
        double review = spot.getReviewScore() == null ? 0 : spot.getReviewScore().doubleValue();
        // 混雑していないこと 65%、口コミ 35% の重みで並べます。
        return (100 - crowd) * 0.65 + review * 20 * 0.35;
    }
}
