package com.example.news.ai;

import java.util.List;

public record AiResponse(
        String text,
        Content content,
        List<Citation> citations,
        String model,
        boolean cached
) {
    public AiResponse {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public record Content(List<Section> sections) {
        public Content {
            sections = sections == null ? List.of() : List.copyOf(sections);
        }
    }

    public record Section(String heading, List<Point> items) {
        public Section {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record Point(String text, List<Integer> sourceIds) {
        public Point {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }

    public record Citation(int id, String source, String title, String url) {}
}
