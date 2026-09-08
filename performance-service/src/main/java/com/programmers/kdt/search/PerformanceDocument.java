package com.programmers.kdt.search;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.KnnSimilarity;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

@Document(indexName = "performances")
@Setting(settingPath = "elasticsearch/performance-settings.json")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceDocument {
    @Id
    @Field(type = FieldType.Long)
    private Long performanceId;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "korean_index", searchAnalyzer = "korean_search"),
            otherFields = {
                    @InnerField(suffix="auto", type = FieldType.Text, analyzer = "korean_index", searchAnalyzer = "korean_search")
            }
    )
    private String title;

    @Field(type = FieldType.Text, analyzer = "korean_index", searchAnalyzer = "korean_search")
    private String description;

    // RAG 추천용 - title+description을 OpenAI text-embedding-3-small(1536차원)로 벡터화한 값.
    // dims는 @Field가 컴파일 타임 상수만 받아서 application.properties의 openai.embedding-dims와
    // 자동 동기화되지 않음 - 임베딩 모델을 바꾸면 이 값도 같이 수동으로 맞춰야 함.
    //
    // 이 애너테이션은 인덱스 매핑(dense_vector, dims, cosine)을 생성하는 용도로만 쓴다 - 실제 색인은
    // PerformanceDocumentSaveEventListener가 이 클래스를 안 쓰고 순수 RestClient로 ES REST API를
    // 직접 호출해서 한다(이 프로젝트의 다른 외부 API 호출과 같은 패턴). 참고로 GET으로 조회했을 때
    // embedding이 안 보이는 건 저장이 안 된 게 아니라 dense_vector가 기본 _source 응답에서 빠지는
    // ES의 정상 동작(_source_includes=embedding으로 명시해야 나옴)이니 헷갈리지 말 것 -
    // RecommendationService의 kNN 검색도 이 필드 값을 직접 안 읽고(스코어 기반 순위만 씀) 잘 동작함.
    @Field(type = FieldType.Dense_Vector, dims = 1536, knnSimilarity = KnnSimilarity.COSINE)
    private Float[] embedding;
}
