package com.example.news.service;

import com.example.news.model.NewsApiResult;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CacheService {

    private static final String CACHE_VERSION = "v2";

    @Cacheable(value = "newsSearch", key = "T(com.example.news.service.CacheService).cacheKey(#keyword, #page, #pageSize)", unless = "#result == null")
    public NewsApiResult load(String keyword, int page, int pageSize) {
        return null;
    }

    @CachePut(value = "newsSearch", key = "T(com.example.news.service.CacheService).cacheKey(#keyword, #page, #pageSize)")
    public NewsApiResult save(String keyword, int page, int pageSize, NewsApiResult result) {
        return result;
    }

    public static String cacheKey(String keyword, int page, int pageSize) {
        return CACHE_VERSION + "_" + keyword.toLowerCase() + "_" + page + "_" + pageSize;
    }
}
