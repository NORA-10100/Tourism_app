package com.example.localspot.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.example.localspot.service.LocalSpotService.Spot;



@Controller
public class LocalSpotController {
    private final LocalSpotService localSpotService;

    public LocalSpotController(LocalSpotService localSpotService) {
        this.localSpotService = localSpotService;
    }

    @GetMapping("/features/spots")
    public String spots(@RequestParam(defaultValue = "") String area,
            @RequestParam(defaultValue = "") String category, Model model) {
        List<Spot> recommendations = localSpotService.recommend(area, category);
        model.addAttribute("areas", localSpotService.areas());
        model.addAttribute("categories", localSpotService.categories());
        model.addAttribute("selectedArea", area);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("spots", recommendations);
        model.addAttribute("recommendationMessage", recommendations.isEmpty()
                ? "条件に合うスポットが見つかりませんでした。エリアやカテゴリを変えてお試しください。"
                : "選んだ条件に合わせて、地域ならではのスポットを提案します。");
        return "spots";
    }

    @GetMapping("/features/spots/{id}")
    public String detail(@PathVariable String id, Model model) {
        Spot spot = localSpotService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("spot", spot);
        return "spot-detail";
    }
}
