package com.example.news.client.nyt;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "nytFeignClient", url = "https://api.nytimes.com")
public interface NytFeignClient {

    @GetMapping("/svc/search/v2/articlesearch.json")
    Map<String, Object> search(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam("page") int page,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam("api-key") String apiKey
    );
}
