package com.programmers.kdt.recommendation.service;

import co.elastic.clients.elasticsearch._types.KnnSearch;
import com.programmers.kdt.recommendation.client.OpenAiChatClient;
import com.programmers.kdt.recommendation.client.OpenAiEmbeddingClient;
import com.programmers.kdt.recommendation.dto.RecommendationResponse;
import com.programmers.kdt.recommendation.dto.RecommendedPerformance;
import com.programmers.kdt.search.PerformanceDocument;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

/**
 * RAG 3단계를 그대로 메서드로 구현한다: embed(질의 벡터화, Retrieval 준비) -> kNN 검색(Retrieval)
 * -> 프롬프트 구성(Augmentation) -> chatClient.generate(Generation).
 * 순수 벡터 유사도 목록만 반환하면 RAG가 아니라 "벡터 검색 기반 추천"이라, reason(추천 이유)까지
 * LLM이 생성해서 붙이는 게 이 서비스의 핵심 포인트다.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int TOP_K = 5;
    private static final String EMBEDDING_FIELD = "embedding";
    private static final String SYSTEM_PROMPT =
            "당신은 공연 티켓 예매 서비스의 추천 도우미입니다. "
                    + "주어진 후보 공연 목록 중에서 사용자 취향에 가장 잘 맞는 공연을 골라, "
                    + "그 이유를 한국어로 3문장 이내로 간결하게 설명하세요.";

    private final OpenAiEmbeddingClient embeddingClient;
    private final OpenAiChatClient chatClient;
    private final ElasticsearchOperations elasticsearchOperations;

    public RecommendationResponse recommend(String query) {
        List<Float> queryEmbedding = embeddingClient.embed(query);
        List<PerformanceDocument> candidates = findSimilarPerformances(queryEmbedding);

        if (candidates.isEmpty()) {
            return new RecommendationResponse(List.of(), "조건에 맞는 공연을 찾지 못했습니다.");
        }

        String reason = chatClient.generate(SYSTEM_PROMPT, buildUserPrompt(query, candidates));
        List<RecommendedPerformance> performances = candidates.stream()
                .map(RecommendedPerformance::from)
                .toList();

        return new RecommendationResponse(performances, reason);
    }

    private List<PerformanceDocument> findSimilarPerformances(List<Float> queryEmbedding) {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withKnnSearches(KnnSearch.of(knn -> knn
                        .field(EMBEDDING_FIELD)
                        .queryVector(queryEmbedding)
                        .k(TOP_K)
                        .numCandidates(TOP_K * 10)))
                .build();

        return elasticsearchOperations.search(nativeQuery, PerformanceDocument.class)
                .stream()
                .map(SearchHit::getContent)
                .toList();
    }

    private String buildUserPrompt(String query, List<PerformanceDocument> candidates) {
        String context = candidates.stream()
                .map(doc -> "- %s: %s".formatted(doc.getTitle(), doc.getDescription()))
                .collect(Collectors.joining("\n"));

        return """
                사용자 질의: %s

                후보 공연 목록:
                %s

                위 후보 중 사용자 취향에 가장 잘 맞는 공연을 추천하고, 이유를 설명해줘.
                """.formatted(query, context);
    }
}
