package com.example.kakeibo.spot;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.kakeibo.language.LanguageSupport;

/**
 * 選択したスポットと日数から、日別タイムライン形式の旅行プランを組み立てます。
 *
 * <p>外部の LLM を使わず、距離とカテゴリ別の滞在時間から決まった手順で生成します。
 * LLM や経路探索 API を接続する場合は、このクラスの生成処理を差し替えます。</p>
 */
@Service
public class TravelPlanService {

    private static final LocalTime START_TIME = LocalTime.of(9, 0);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final double AVERAGE_SPEED_KMH = 30.0;
    private static final int MAX_DAYS = 4;

    private final SpotRepository spotRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final PlanSpotRepository planSpotRepository;
    private final SpotService spotService;
    private final MessageSource messages;

    public TravelPlanService(SpotRepository spotRepository,
            TravelPlanRepository travelPlanRepository,
            PlanSpotRepository planSpotRepository,
            SpotService spotService,
            MessageSource messages) {
        this.spotRepository = spotRepository;
        this.travelPlanRepository = travelPlanRepository;
        this.planSpotRepository = planSpotRepository;
        this.spotService = spotService;
        this.messages = messages;
    }

    @Transactional
    public TravelPlanView generate(Integer userId, List<Integer> spotIds,
            int requestedDays, String language) {

        // メモ: 日数の上限を守り、選択順を維持してプランと明細を作ります。
        int days = Math.max(1, Math.min(requestedDays, MAX_DAYS));

        Map<Integer, Spot> found = spotRepository.findAllById(spotIds).stream()
                .collect(Collectors.toMap(Spot::getId, Function.identity()));
        List<Spot> ordered = spotIds.stream()
                .map(found::get)
                .filter(Objects::nonNull)
                .toList();
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("spots are empty");
        }

        TravelPlan plan = travelPlanRepository.save(new TravelPlan(userId, days));

        List<List<Spot>> buckets = buckets(ordered, days);
        List<TravelPlanView.PlanDay> dayList = new ArrayList<>();
        List<PlanSpot> planSpots = new ArrayList<>();
        Locale locale = LanguageSupport.toLocale(language);

        for (int dayIndex = 0; dayIndex < days; dayIndex++) {
            LocalTime time = START_TIME;
            Spot previous = null;
            int order = 1;
            List<TravelPlanView.PlanEntry> entries = new ArrayList<>();

            for (Spot spot : buckets.get(dayIndex)) {
                String travelNote = "";
                if (previous != null) {
                    double km = distanceKm(previous, spot);
                    int minutes = travelMinutes(km);
                    time = time.plusMinutes(minutes);
                    travelNote = messages.getMessage("spot.plan.move",
                            new Object[] { minutes, formatDistance(km) }, locale);
                }

                int stay = stayMinutes(spot);
                entries.add(new TravelPlanView.PlanEntry(
                        time.format(TIME_FORMAT),
                        spotService.viewOf(spot, language),
                        stay,
                        travelNote));

                planSpots.add(new PlanSpot(
                        plan.getId(), spot.getId(), dayIndex + 1, order, stay));
                time = time.plusMinutes(stay);
                previous = spot;
                order++;
            }

            dayList.add(new TravelPlanView.PlanDay(dayIndex + 1, entries));
        }

        planSpotRepository.saveAll(planSpots);
        return new TravelPlanView(plan.getId(), days, dayList);
    }

    @Transactional(readOnly = true)
    public TravelPlanView load(Integer planId, Integer userId, String language) {
        // メモ: URLのIDだけで他人のプランを参照できないよう、作成者を必ず照合します。
        TravelPlan plan = travelPlanRepository.findById(planId)
                .orElseThrow(NoSuchElementException::new);
        if (userId == null || !userId.equals(plan.getUserId())) {
            throw new NoSuchElementException("plan not found");
        }

        List<PlanSpot> planSpots = planSpotRepository
                .findByPlanIdOrderByDayNumberAscVisitOrderAsc(planId);
        Map<Integer, Spot> spots = spotRepository.findAllById(
                planSpots.stream().map(PlanSpot::getSpotId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Spot::getId, Function.identity()));

        Locale locale = LanguageSupport.toLocale(language);
        List<TravelPlanView.PlanDay> dayList = new ArrayList<>();

        for (int day = 1; day <= plan.getDays(); day++) {
            LocalTime time = START_TIME;
            Spot previous = null;
            List<TravelPlanView.PlanEntry> entries = new ArrayList<>();

            for (PlanSpot planSpot : planSpots) {
                if (planSpot.getDayNumber() != day) {
                    continue;
                }
                Spot spot = spots.get(planSpot.getSpotId());
                if (spot == null) {
                    continue;
                }

                String travelNote = "";
                if (previous != null) {
                    double km = distanceKm(previous, spot);
                    int minutes = travelMinutes(km);
                    time = time.plusMinutes(minutes);
                    travelNote = messages.getMessage("spot.plan.move",
                            new Object[] { minutes, formatDistance(km) }, locale);
                }

                Integer savedStayMinutes = planSpot.getStayMinutes();
                int stay = savedStayMinutes == null
                        ? stayMinutes(spot)
                        : savedStayMinutes;
                entries.add(new TravelPlanView.PlanEntry(
                        time.format(TIME_FORMAT),
                        spotService.viewOf(spot, language),
                        stay,
                        travelNote));

                time = time.plusMinutes(stay);
                previous = spot;
            }

            dayList.add(new TravelPlanView.PlanDay(day, entries));
        }

        return new TravelPlanView(plan.getId(), plan.getDays(), dayList);
    }

    /** 選択順を保ったまま、日ごとに均等な数へ振り分けます。 */
    private static List<List<Spot>> buckets(List<Spot> spots, int days) {
        List<List<Spot>> result = new ArrayList<>();
        int base = spots.size() / days;
        int extra = spots.size() % days;
        int index = 0;

        for (int day = 0; day < days; day++) {
            int count = base + (day < extra ? 1 : 0);
            result.add(new ArrayList<>(spots.subList(index, index + count)));
            index += count;
        }
        return result;
    }

    private static int stayMinutes(Spot spot) {
        return switch (spot.getCategory()) {
            case "自然" -> 120;
            case "文化" -> 90;
            case "グルメ" -> 75;
            case "体験" -> 120;
            default -> 90;
        };
    }

    private static int travelMinutes(double km) {
        double raw = km / AVERAGE_SPEED_KMH * 60.0;
        return (int) Math.max(5, Math.round(raw / 5.0) * 5);
    }

    private static String formatDistance(double km) {
        return String.format(Locale.ROOT, "%.1f", km);
    }

    /** 2地点間の直線距離（km）を求めます。 */
    private static double distanceKm(Spot from, Spot to) {
        if (from.getLatitude() == null || from.getLongitude() == null
                || to.getLatitude() == null || to.getLongitude() == null) {
            return 0;
        }
        double lat1 = Math.toRadians(from.getLatitude().doubleValue());
        double lat2 = Math.toRadians(to.getLatitude().doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLng = Math.toRadians(
                to.getLongitude().doubleValue() - from.getLongitude().doubleValue());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
