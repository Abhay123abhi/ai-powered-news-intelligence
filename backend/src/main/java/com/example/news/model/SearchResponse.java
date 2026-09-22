package com.example.news.model;

import java.util.List;

public record SearchResponse(
        String newsWebsite,
        String searchKeyword,
        String city,
        int page,
        int pageSize,
        int totalArticles,
        int totalPages,
        Integer prevPage,
        Integer nextPage,
        long timeTakenMs,
        List<NewsArticle> articles
) {
}
