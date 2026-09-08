package com.programmers.kdt.recommendation.dto;

import java.util.List;

public record RecommendationResponse(
        List<RecommendedPerformance> performances,
        String reason
) {
}
