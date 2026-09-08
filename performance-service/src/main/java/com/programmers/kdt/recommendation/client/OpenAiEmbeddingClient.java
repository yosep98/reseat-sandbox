package com.programmers.kdt.recommendation.client;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.recommendation.exception.RecommendationErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenAI Embeddings API로 텍스트를 벡터로 변환한다.
 * 공연 인덱싱 시(제목+설명 -> 벡터)와 추천 질의 시(사용자 질의 -> 벡터) 둘 다 이 클라이언트를 쓴다
 * - 같은 임베딩 모델로 만든 벡터끼리만 코사인 유사도 비교가 의미 있기 때문에 인덱싱/질의가 반드시 같은 모델을 써야 한다.
 */
@Component
public class OpenAiEmbeddingClient {

    private final RestClient restClient;
    private final String model;

    public OpenAiEmbeddingClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.embedding-model}") String model) {
        this.model = model;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com")
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public List<Float> embed(String text) {
        try {
            EmbeddingResponse response = restClient.post()
                    .uri("/v1/embeddings")
                    .body(Map.of("model", model, "input", text))
                    .retrieve()
                    .body(EmbeddingResponse.class);

            return response.data().get(0).embedding();
        } catch (RestClientException e) {
            throw new BusinessException(RecommendationErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(List<Float> embedding) {
    }
}
