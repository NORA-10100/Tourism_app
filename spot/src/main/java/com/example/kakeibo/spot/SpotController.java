package com.example.kakeibo.spot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.example.kakeibo.common.SessionUser;

import jakarta.servlet.http.HttpSession;

/**
 * ローカルスポット提案の画面（条件入力・一覧/地図・詳細・日数選択・旅行プラン）を
 * 受け持ちます。画面ID 1-1-1 〜 1-5-1 に対応します。
 */
@Controller
@RequestMapping("/spots")
public class SpotController {

    private static final Logger logger = LoggerFactory.getLogger(SpotController.class);
    private static final String SELECTION_KEY = "selectedSpotIds";
    private static final int MAX_DAYS = 4;

    private final SpotService spotService;
    private final SpotRecommendationService recommendationService;
    private final TravelPlanService travelPlanService;
    private final MessageSource messages;

    public SpotController(SpotService spotService,
            SpotRecommendationService recommendationService,
            TravelPlanService travelPlanService,
            MessageSource messages) {
        this.spotService = spotService;
        this.recommendationService = recommendationService;
        this.travelPlanService = travelPlanService;
        this.messages = messages;
    }

    /** 条件入力画面（1-1-1）。 */
    @GetMapping
    public String index(HttpSession session, Model model) {
        if (!SessionUser.isLoggedIn(session)) {
            return "redirect:/login";
        }
        String language = SessionUser.language(session);
        model.addAttribute("areas", spotService.areas());
        model.addAttribute("categories", spotService.categoryOptions(language));
        model.addAttribute("selectedArea", "");
        model.addAttribute("selectedCategory", "");
        return "spot/index";
    }

    /** スポット一覧／地図画面（1-2-1）。 */
    @GetMapping("/results")
    public String results(@RequestParam(required = false) String area,
            @RequestParam(required = false) String category,
            HttpSession session, Model model) {
        if (!SessionUser.isLoggedIn(session)) {
            return "redirect:/login";
        }

        String language = SessionUser.language(session);
        model.addAttribute("spots", recommendationService.recommend(area, category, language));
        model.addAttribute("area", area == null ? "" : area);
        model.addAttribute("category", category == null ? "" : category);
        model.addAttribute("areas", spotService.areas());
        model.addAttribute("categories", spotService.categoryOptions(language));
        model.addAttribute("selectedIds", selectedIds(session));
        return "spot/results";
    }

    /** スポット詳細画面（1-3-1）。 */
    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id, HttpSession session, Model model) {
        if (!SessionUser.isLoggedIn(session)) {
            return "redirect:/login";
        }

        String language = SessionUser.language(session);
        SpotView spot = spotService.view(id, language)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("spot", spot);
        model.addAttribute("selected", selectedIds(session).contains(id));
        return "spot/detail";
    }

    /** 詳細画面のチェックボックスで、旅行プランに入れるスポットを切り替えます。 */
    @PostMapping("/selection")
    public String toggleSelection(@RequestParam Integer spotId, HttpSession session) {
        if (!SessionUser.isLoggedIn(session)) {
            return "redirect:/login";
        }
        spotService.findActive(spotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Set<Integer> selected = selectedIds(session);
        if (!selected.add(spotId)) {
            selected.remove(spotId);
        }
        session.setAttribute(SELECTION_KEY, selected);
        return "redirect:/spots/" + spotId;
    }

    /** 日数選択画面（1-4-1）。 */
    @GetMapping("/days")
    public String days(HttpSession session, Model model) {
        if (!SessionUser.isLoggedIn(session)) {
            return "redirect:/login";
        }
        return showDays(session, model);
    }

    /** 選択スポットと日数からモデルコースを作成する画面遷移（1-5-1）。 */
    @PostMapping("/plan")
    public String generate(@RequestParam(required = false) Integer days,
            HttpSession session, Model model) {
        if (!SessionUser.isLoggedIn(session)) {
            return "redirect:/login";
        }

        if (days == null || days < 1 || days > MAX_DAYS) {
            model.addAttribute("error", message("spot.days.required"));
            return showDays(session, model);
        }

        List<Integer> spotIds = new ArrayList<>(selectedIds(session));
        if (spotIds.isEmpty()) {
            model.addAttribute("error", message("spot.days.empty"));
            return showDays(session, model);
        }

        Integer userId = SessionUser.userId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        try {
            TravelPlanView plan = travelPlanService.generate(
                    userId, spotIds, days, SessionUser.language(session));
            return "redirect:/spots/plan/" + plan.id();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", message("spot.days.empty"));
            return showDays(session, model);
        } catch (DataAccessException e) {
            logger.error("旅行プランを保存できませんでした。", e);
            model.addAttribute("error", message("spot.plan.failed"));
            return showDays(session, model);
        }
    }

    /** 保存済みの旅行プラン表示画面（1-5-1）。 */
    @GetMapping("/plan/{id}")
    public String plan(@PathVariable Integer id, HttpSession session, Model model) {
        if (!SessionUser.isLoggedIn(session)) {
            return "redirect:/login";
        }

        try {
            TravelPlanView plan = travelPlanService.load(
                    id, SessionUser.userId(session), SessionUser.language(session));
            model.addAttribute("plan", plan);
            return "spot/plan";
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** 日数選択画面の選択肢です。表示言語に合わせた文言を入れておきます。 */
    private record DayOption(int value, String label) {
    }

    private String showDays(HttpSession session, Model model) {
        // メモ: 入力エラーで画面を再表示するときも、選択スポットと日数候補を戻します。
        String language = SessionUser.language(session);
        model.addAttribute("spots", spotService.viewsByIds(selectedIds(session), language));
        model.addAttribute("dayOptions", List.of(
                new DayOption(1, message("spot.days.day1")),
                new DayOption(2, message("spot.days.day2")),
                new DayOption(3, message("spot.days.day3")),
                new DayOption(4, message("spot.days.day4"))));
        return "spot/days";
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    private static Set<Integer> selectedIds(HttpSession session) {
        // メモ: セッション値を型確認して読み、破損・古い値を選択一覧へ混ぜません。
        Object value = session.getAttribute(SELECTION_KEY);
        Set<Integer> result = new LinkedHashSet<>();
        if (value instanceof Set<?> stored) {
            for (Object item : stored) {
                if (item instanceof Integer id) {
                    result.add(id);
                }
            }
        }
        return result;
    }
}
