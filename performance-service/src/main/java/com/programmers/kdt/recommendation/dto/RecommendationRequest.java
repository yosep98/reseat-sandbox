package com.programmers.kdt.recommendation.dto;

import jakarta.validation.constraints.NotBlank;

public record RecommendationRequest(
        @NotBlank(message = "추천 질의는 비어있을 수 없습니다.")
        String query
) {
}
