package com.programmers.kdt.recommendation.dto;

import com.programmers.kdt.search.PerformanceDocument;

public record RecommendedPerformance(
        Long performanceId,
        String title
) {
    public static RecommendedPerformance from(PerformanceDocument document) {
        return new RecommendedPerformance(document.getPerformanceId(), document.getTitle());
    }
}
