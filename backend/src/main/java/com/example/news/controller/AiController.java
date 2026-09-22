package com.example.news.controller;

import com.example.news.ai.*;
import com.example.news.service.FeedStore;
import com.example.news.model.NewsArticle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiInsightService insights;
    private final FeedStore feeds;
    public AiController(AiInsightService insights, FeedStore feeds) {
        this.insights = insights;
        this.feeds = feeds;
    }
    @GetMapping("/status")
    public AiStatus status() { return new AiStatus(insights.isEnabled(), insights.isEnabled() ? "ready" : "disabled"); }
    @PostMapping("/brief")
    public AiResponse brief(@Valid @RequestBody Selection request) { return insights.dailyBrief(resolve(request)); }
    @PostMapping("/compare")
    public AiResponse compare(@Valid @RequestBody Selection request) {
        List<NewsArticle> articles = resolve(request);
        if (articles.stream().map(NewsArticle::source).distinct().count() < 2) {
            throw new IllegalArgumentException("Select stories from at least two publishers to compare coverage.");
        }
        return insights.compare(articles);
    }
    @PostMapping("/ask")
    public AiResponse ask(@Valid @RequestBody Question request) {
        return insights.ask(request.question(), resolve(request.selection()));
    }
    @PostMapping("/summary")
    public AiResponse summary(@Valid @RequestBody Selection request) { return insights.summarize(single(request)); }
    @PostMapping("/why-it-matters")
    public AiResponse why(@Valid @RequestBody Selection request) { return insights.explainWhyItMatters(single(request)); }
    private NewsArticle single(Selection request) {
        if (request.articleIds().size() != 1) throw new IllegalArgumentException("Select exactly one story.");
        return resolve(request).getFirst();
    }
    private List<NewsArticle> resolve(Selection request) {
        return feeds.resolve(request.feedId(), request.page(), request.articleIds());
    }
    public record AiStatus(boolean enabled, String state) { }
    public record Selection(@NotBlank @Pattern(regexp = "[a-f0-9-]{36}") String feedId,
            @Min(1) @Max(100) int page,
            @NotEmpty @Size(max = 8) List<@NotNull @Min(0) @Max(24) Integer> articleIds) { }
    public record Question(@NotBlank @Size(max = 500) String question,
            @NotNull @Valid Selection selection) { }
}
