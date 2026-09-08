package com.programmers.kdt.recommendation.controller;

import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.recommendation.dto.RecommendationRequest;
import com.programmers.kdt.recommendation.dto.RecommendationResponse;
import com.programmers.kdt.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performances")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @PostMapping("/recommendations")
    public ApiResponse<RecommendationResponse> recommend(@Valid @RequestBody RecommendationRequest request) {
        return ApiResponse.success(recommendationService.recommend(request.query()));
    }
}
