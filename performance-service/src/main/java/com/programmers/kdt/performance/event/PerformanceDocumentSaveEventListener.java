package com.programmers.kdt.performance.event;

import com.programmers.kdt.performance.entity.Performance;
import com.programmers.kdt.recommendation.client.OpenAiEmbeddingClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 공연 저장 후 검색/추천용 Elasticsearch 문서를 색인한다.
 * PerformanceSearchRepository(spring-data-elasticsearch) 대신 이 프로젝트의 다른 외부 API 호출
 * (TossPgclient, OpenAiEmbeddingClient)과 같은 방식 - 순수 RestClient로 ES REST API를 직접 호출 -
 * 을 쓴다. 참고로 GET으로 조회했을 때 embedding 필드가 안 보이는 건 저장이 안 된 게 아니라,
 * dense_vector 필드는 기본 _source 응답에서 빠지는 ES의 정상 동작(_source_includes=embedding으로
 * 명시해야 나옴)이라 헷갈리기 쉬웠다 - kNN 검색 자체는 _source 노출 여부와 무관하게 잘 동작한다.
 */
@Slf4j
@Component
public class PerformanceDocumentSaveEventListener {

    private static final String INDEX_NAME = "performances";

    private final RestClient restClient;
    private final OpenAiEmbeddingClient embeddingClient;

    public PerformanceDocumentSaveEventListener(
            @Value("${spring.elasticsearch.uris}") String elasticsearchUri,
            OpenAiEmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(elasticsearchUri)
                .requestFactory(requestFactory)
                .build();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void handleDocumentSave(PerformanceDocumentSaveEvent event) {
        Performance performance = event.performance();
        List<Float> embedding = tryEmbed(performance);

        Map<String, Object> document = new HashMap<>();
        document.put("performanceId", performance.getPerformanceId());
        document.put("title", performance.getTitle());
        document.put("description", performance.getDescription());
        if (embedding != null) {
            document.put("embedding", embedding);
        }

        try {
            restClient.put()
                    .uri("/{index}/_doc/{id}", INDEX_NAME, performance.getPerformanceId())
                    .body(document)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("검색 색인 실패 (performanceId={})", performance.getPerformanceId(), e);
        }
    }

    /**
     * 임베딩(추천 기능용 부가 데이터)이 실패해도 검색 색인 자체는 막히면 안 되므로 fail-open으로 처리한다.
     * embedding이 null이면 키워드 검색은 그대로 되고, 이 공연만 벡터 추천 대상에서 빠진다.
     */
    private List<Float> tryEmbed(Performance performance) {
        try {
            return embeddingClient.embed(performance.getTitle() + " " + performance.getDescription());
        } catch (Exception e) {
            log.warn("공연 임베딩 생성 실패 - 추천 대상에서 제외하고 색인은 계속 진행 (performanceId={})",
                    performance.getPerformanceId(), e);
            return null;
        }
    }
}
