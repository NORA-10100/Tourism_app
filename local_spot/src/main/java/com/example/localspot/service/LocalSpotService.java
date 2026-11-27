package com.example.localspot.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class LocalSpotService {
    private static final List<Spot> SPOTS = List.of(
            new Spot("1", "国際通りの路地裏カフェ", "那覇市", "グルメ", "カフェ", "沖縄素材のスイーツとコーヒーを楽しめる、落ち着いた路地裏のカフェ。", "10:00〜18:00（目安）", "ゆいレール牧志駅から徒歩約5分（目安）", 4.8, 24, 18, "cafe"),
            new Spot("2", "瀬長島ウミカジテラス", "豊見城市", "文化", "海辺", "海を眺めながら島の食や工芸にふれられ、夕景も楽しめます。", "店舗により異なります", "那覇空港から車で約15分（目安）", 4.6, 98, 72, "island"),
            new Spot("3", "壺屋やちむん通り", "那覇市", "文化", "まち歩き", "焼き物の工房や古い街並みが残る通り。器探しを楽しめます。", "店舗により異なります", "牧志駅から徒歩約10分（目安）", 4.7, 31, 22, "town"),
            new Spot("4", "識名園", "那覇市", "自然", "庭園", "琉球王家ゆかりの庭園をゆっくり散策できます。", "9:00〜18:00（季節変動）", "那覇市内からバスまたは車（目安）", 4.5, 17, 34, "garden"),
            new Spot("5", "知念岬公園", "南城市", "自然", "海辺", "太平洋を見渡せる開放的な岬。海風を感じたい時におすすめです。", "終日（天候に注意）", "那覇市内から車で約45分（目安）", 4.6, 12, 38, "island"),
            new Spot("6", "浜比嘉島の集落さんぽ", "うるま市", "体験", "まち歩き", "昔ながらの集落と海辺の景色を静かに楽しめます。", "終日（集落では静かに）", "那覇市内から車で約1時間（目安）", 4.5, 9, 27, "town"),
            new Spot("7", "牧志公設市場周辺の食べ歩き", "那覇市", "グルメ", "市場", "沖縄の食材や惣菜に出会える市場周辺で、地元の味を楽しめます。", "店舗により異なります", "牧志駅から徒歩約8分（目安）", 4.4, 46, 48, "cafe"),
            new Spot("8", "浦添大公園の展望エリア", "浦添市", "自然", "公園", "高台から街と海を眺め、広い空の下でひと休みできます。", "終日（施設により異なる）", "那覇市内から車で約25分（目安）", 4.3, 8, 20, "garden"));

    public List<String> areas() { return SPOTS.stream().map(Spot::area).distinct().sorted().toList(); }
    public List<String> categories() { return List.of("自然", "グルメ", "文化", "体験"); }

    public List<Spot> recommend(String area, String category) {
        String selectedArea = area == null ? "" : area.trim();
        String selectedCategory = category == null ? "" : category.trim();
        return SPOTS.stream()
                .filter(spot -> selectedArea.isEmpty() || spot.area().equals(selectedArea))
                .filter(spot -> selectedCategory.isEmpty() || spot.category().equals(selectedCategory))
                .sorted(Comparator.comparingInt(Spot::crowdScore)
                        .thenComparing(Spot::rating, Comparator.reverseOrder()))
                .toList();
    }

    public Optional<Spot> findById(String id) {
        return SPOTS.stream().filter(spot -> spot.id().equals(id)).findFirst();
    }

    public record Spot(String id, String name, String area, String category, String feature,
            String description, String businessHours, String access, double rating,
            int reviewCount, int crowdScore, String visualStyle) { }
}
