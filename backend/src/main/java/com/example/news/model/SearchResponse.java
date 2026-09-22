package com.example.news.model;

import java.util.List;

public record SearchResponse(
        String searchKeyword, String feedId, int page, int pageSize,
        Integer prevPage, Integer nextPage, long timeTakenMs,
        String fetchedAt, boolean partial, List<String> unavailableSources,
        boolean limited, List<NewsArticle> articles
) { }
