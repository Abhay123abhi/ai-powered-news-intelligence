package com.example.news.controller;

import com.example.news.model.SearchResponse;
import com.example.news.service.AggregationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final AggregationService service;

    public NewsController(AggregationService service) {
        this.service = service;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam(required = false) @Size(max = 120) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(25) Integer pageSize,
            @RequestParam(required = false) @Size(max = 36) String feedId
    ) {
        return service.search(keyword, page, pageSize, feedId);
    }
}
