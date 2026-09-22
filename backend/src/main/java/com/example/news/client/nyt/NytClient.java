package com.example.news.client.nyt;

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
public class NytClient implements NewsProviderClient {

    private static final Logger log = LoggerFactory.getLogger(NytClient.class);

    private final NytFeignClient feignClient;

    public NytClient(NytFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Value("${nyt.api.key}")
    private String apiKey;

    @Override
    public String getProviderName() {
        return "NYT";
    }

    @Override
    public NewsApiResult search(String keyword, int page, int pageSize) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new NewsProviderException("NYT API key is not configured");
        }

        try {
            // NYT ranks by relevance by default when q is present. Only force newest for the unfiltered latest feed.
            String sort = keyword == null || keyword.isBlank() ? "newest" : null;
            Map<String, Object> responseMap =
                    feignClient.search(keyword, Math.max(0, page - 1), sort, apiKey);

            Map<String, Object> response =
                    (Map<String, Object>) responseMap.get("response");

            if (response == null) {
                throw new NewsProviderException("NYT returned an invalid response");
            }

            Map<String, Object> meta =
                    (Map<String, Object>) response.getOrDefault("meta", Collections.emptyMap());

            int totalResults = number(meta.get("hits"));
            // The NYT Article Search API always returns ten documents per page.
            int totalPages = (int) Math.ceil(totalResults / 10.0);

            List<Map<String, Object>> docs =
                    (List<Map<String, Object>>) response.getOrDefault("docs", Collections.emptyList());

            List<NewsArticle> articles = new ArrayList<>();

            for (Map<String, Object> doc : docs) {
                Map<String, Object> headline = (Map<String, Object>) doc.get("headline");

                String imageUrl = extractImageUrl(doc.get("multimedia"));

                if (imageUrl == null || imageUrl.isBlank()) {
                    imageUrl = "https://placehold.co/600x400?text=No+Image";
                }

                articles.add(new NewsArticle(
                        headline != null ? (String) headline.get("main") : null,
                        (String) doc.getOrDefault("abstract", doc.get("snippet")),
                        (String) doc.get("web_url"),
                        "The New York Times",
                        (String) doc.get("pub_date"),
                        imageUrl
                ));
            }

            return new NewsApiResult(totalResults, totalPages, articles);

        } catch (Exception ex) {
            log.warn("NYT request failed ({})", ex.getClass().getSimpleName());
            if (ex instanceof NewsProviderException providerException) {
                throw providerException;
            }
            throw new NewsProviderException("NYT request failed", ex);
        }
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private String extractImageUrl(Object multimedia) {
        String imageUrl = null;

        if (multimedia instanceof Map<?, ?> mediaMap) {
            Object defaultImage = mediaMap.get("default");
            if (defaultImage instanceof Map<?, ?> imageMap) {
                imageUrl = Objects.toString(imageMap.get("url"), null);
            }
        } else if (multimedia instanceof List<?> mediaList) {
            imageUrl = mediaList.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(image -> Objects.toString(image.get("url"), null))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        if (imageUrl != null && imageUrl.startsWith("/")) {
            return "https://www.nytimes.com" + imageUrl;
        }
        return imageUrl;
    }
}
