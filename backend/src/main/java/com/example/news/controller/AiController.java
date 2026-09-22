package com.example.news.controller;

import com.example.news.ai.AiInsightService;
import com.example.news.ai.AiResponse;
import com.example.news.model.NewsArticle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiInsightService aiInsightService;

    public AiController(AiInsightService aiInsightService) {
        this.aiInsightService = aiInsightService;
    }

    @GetMapping("/status")
    public ResponseEntity<AiStatus> status() {
        return ResponseEntity.ok(new AiStatus(aiInsightService.isEnabled()));
    }

    @PostMapping("/summary")
    public ResponseEntity<AiResponse> summarize(@Valid @RequestBody ArticleRequest request) {
        return ResponseEntity.ok(aiInsightService.summarize(request.article()));
    }

    @PostMapping("/why-it-matters")
    public ResponseEntity<AiResponse> whyItMatters(@Valid @RequestBody ArticleRequest request) {
        return ResponseEntity.ok(aiInsightService.explainWhyItMatters(request.article()));
    }

    @PostMapping("/brief")
    public ResponseEntity<AiResponse> brief(@Valid @RequestBody ArticlesRequest request) {
        return ResponseEntity.ok(aiInsightService.dailyBrief(request.articles()));
    }

    @PostMapping("/compare")
    public ResponseEntity<AiResponse> compare(@Valid @RequestBody ArticlesRequest request) {
        return ResponseEntity.ok(aiInsightService.compare(request.articles()));
    }

    @PostMapping("/ask")
    public ResponseEntity<AiResponse> ask(@Valid @RequestBody AskRequest request) {
        return ResponseEntity.ok(aiInsightService.ask(request.question(), request.articles()));
    }

    public record AiStatus(boolean enabled) {}
    public record ArticleRequest(@NotNull NewsArticle article) {}
    public record ArticlesRequest(@NotEmpty @Size(max = 20) List<@NotNull NewsArticle> articles) {}
    public record AskRequest(@NotBlank @Size(max = 500) String question, @NotEmpty @Size(max = 20) List<@NotNull NewsArticle> articles) {}
}
