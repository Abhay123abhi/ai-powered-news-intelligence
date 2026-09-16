package com.example.news.client.guardian;

import com.example.news.client.NewsProviderClient;
import com.example.news.exception.NewsProviderException;
import com.example.news.model.NewsApiResult;
import com.example.news.model.NewsArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GuardianClient implements NewsProviderClient {

    private static final Logger log = LoggerFactory.getLogger(GuardianClient.class);

    private final GuardianFeignClient feignClient;

    public GuardianClient(GuardianFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Value("${guardian.api.key}")
    private String apiKey;

    @Override
    public String getProviderName() {
        return "Guardian";
    }

    @Override
    public NewsApiResult search(String keyword, int page, int pageSize) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new NewsProviderException("Guardian API key is not configured");
        }

        try {
            String orderBy = keyword == null || keyword.isBlank() ? "newest" : "relevance";

            Map<String, Object> response =
                    feignClient.search(
                            keyword, Math.max(page, 1),
                            pageSize, "thumbnail,trailText",
                            orderBy, "published", apiKey
                    );

            Map<String, Object> responseData = (Map<String, Object>) response.get("response");

            if (responseData == null) {
                throw new NewsProviderException("Guardian returned an invalid response");
            }

            int totalResults = number(responseData.get("total"));
            int totalPages = number(responseData.get("pages"));

            List<Map<String, Object>> results =
                    (List<Map<String, Object>>) responseData.getOrDefault("results", Collections.emptyList());

            List<NewsArticle> articles = new ArrayList<>();

            for (Map<String, Object> r : results) {
                Map<String, Object> fields = (Map<String, Object>) r.get("fields");

                articles.add(new NewsArticle(
                        (String) r.get("webTitle"),
                        fields != null ? (String) fields.get("trailText") : null,
                        (String) r.get("webUrl"),
                        "The Guardian",
                        (String) r.get("webPublicationDate"),
                        fields != null ? (String) fields.get("thumbnail") : null
                ));
            }

            return new NewsApiResult(totalResults, totalPages, articles);

        } catch (Exception ex) {
            log.error("Guardian API error", ex);
            if (ex instanceof NewsProviderException providerException) {
                throw providerException;
            }
            throw new NewsProviderException("Guardian request failed", ex);
        }
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
