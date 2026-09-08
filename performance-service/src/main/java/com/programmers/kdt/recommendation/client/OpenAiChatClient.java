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
 * OpenAI Chat Completions API로 "왜 이 공연을 추천하는지" 자연어 이유를 생성한다(RAG의 Generation 단계).
 * Retrieval(kNN)로 찾은 후보 공연 목록을 컨텍스트로 넘겨받아, 사용자 질의에 맞춰 근거를 붙여 추천 문구를 만든다.
 */
@Component
public class OpenAiChatClient {

    private final RestClient restClient;
    private final String model;

    public OpenAiChatClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.chat-model}") String model) {
        this.model = model;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com")
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public String generate(String systemPrompt, String userPrompt) {
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(Map.of(
                            "model", model,
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemPrompt),
                                    Map.of("role", "user", "content", userPrompt)
                            )
                    ))
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            return response.choices().get(0).message().content();
        } catch (RestClientException e) {
            throw new BusinessException(RecommendationErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    private record ChatCompletionResponse(List<Choice> choices) {
    }

    private record Choice(ChatMessage message) {
    }

    private record ChatMessage(String role, String content) {
    }
}
