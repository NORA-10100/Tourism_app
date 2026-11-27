package com.example.kakeibo.spot;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.example.kakeibo.language.LanguageSupport;

/**
 * スポットの取得と、画面表示用の値への変換をまとめます。
 */
@Service
public class SpotService {

    /** 条件入力画面のカテゴリ1件分です。value は DB に保存している日本語の値です。 */
    public record CategoryOption(String value, String label) {
    }

    private final SpotRepository spotRepository;
    private final MessageSource messages;

    public SpotService(SpotRepository spotRepository, MessageSource messages) {
        this.spotRepository = spotRepository;
        this.messages = messages;
    }

    public List<Spot> activeSpots() {
        return spotRepository.findByDeleteFlagOrderByCrowdScoreAscReviewScoreDesc(0);
    }

    public List<String> areas() {
        // メモ: 有効スポットからエリアの重複を除き、画面で選びやすい順に並べます。
        return activeSpots().stream()
                .map(Spot::getArea)
                .distinct()
                .sorted()
                .toList();
    }

    public Optional<Spot> findActive(Integer id) {
        return spotRepository.findByIdAndDeleteFlag(id, 0);
    }

    public SpotView viewOf(Spot spot, String language) {
        return SpotView.of(spot, language, messages, LanguageSupport.toLocale(language));
    }

    public List<SpotView> toViews(List<Spot> spots, String language) {
        return spots.stream().map(spot -> viewOf(spot, language)).toList();
    }

    public Optional<SpotView> view(Integer id, String language) {
        return findActive(id).map(spot -> viewOf(spot, language));
    }

    /** 選択した順番を保ったまま、スポットを画面表示用に変換します。 */
    public List<SpotView> viewsByIds(Collection<Integer> ids, String language) {
        Map<Integer, Spot> found = spotRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Spot::getId, Function.identity()));
        List<Spot> ordered = ids.stream()
                .map(found::get)
                .filter(Objects::nonNull)
                .toList();
        return toViews(ordered, language);
    }

    public List<CategoryOption> categoryOptions(String language) {
        // メモ: DBには日本語カテゴリを保持し、画面ラベルだけを選択言語に翻訳します。
        Locale locale = LanguageSupport.toLocale(language);
        return List.of(
                new CategoryOption("", message("spot.category.all", locale)),
                new CategoryOption("自然", message("spot.category.nature", locale)),
                new CategoryOption("グルメ", message("spot.category.gourmet", locale)),
                new CategoryOption("文化", message("spot.category.culture", locale)),
                new CategoryOption("体験", message("spot.category.experience", locale)));
    }

    private String message(String key, Locale locale) {
        return messages.getMessage(key, null, key, locale);
    }
}
