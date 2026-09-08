package com.programmers.kdt.ticket.risk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("공연 조회 -> 좌석 hold 반응속도 추적 (Step7, 절대속도+CV 기반 매크로 의심 판정)")
class TicketReactionTrackerTest {

    private static final Long USER_ID = 100L;
    private static final Long PERFORMANCE_ID = 1L;
    private static final int MIN_SAMPLES = 3;
    private static final long SPEED_THRESHOLD_MS = 800;
    private static final double CV_THRESHOLD = 0.15;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ListOperations<String, String> listOperations;

    private TicketReactionTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new TicketReactionTracker(
                redisTemplate, MIN_SAMPLES, SPEED_THRESHOLD_MS, CV_THRESHOLD,
                600, 604800, 1800);
    }

    @Nested
    @DisplayName("조회 기록이 없는 상태로 hold를 시도하면")
    class NoViewRecorded {

        @Test
        @DisplayName("표본 부족으로 스킵하고 예외를 던지지 않는다")
        void skipsSilently() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("ticket:view:100:1")).willReturn(null);

            tracker.recordReserveAttemptAndScore(USER_ID, PERFORMANCE_ID);

            verify(redisTemplate, never()).opsForList();
        }
    }

    @Nested
    @DisplayName("짧고 일정한 반응시간이 minSamples만큼 쌓이면(매크로 의심)")
    class ConsistentlyFast {

        @Test
        @DisplayName("위험 점수를 기록한다")
        void flagsRisk() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("ticket:view:100:1")).willReturn(String.valueOf(System.currentTimeMillis() - 100));
            given(redisTemplate.opsForList()).willReturn(listOperations);
            // 거의 동일한 반응시간 3개(빠르고 일정함 -> CV 낮음)를 쌓인 표본으로 흉내낸다.
            given(listOperations.range("ticket:reaction-samples:100", 0, -1))
                    .willReturn(List.of("100", "102", "99"));

            tracker.recordReserveAttemptAndScore(USER_ID, PERFORMANCE_ID);

            verify(valueOperations).increment("ticket:risk:100");
            verify(redisTemplate).expire(eq("ticket:risk:100"), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("반응시간이 느리거나 들쭉날쭉하면")
    class NotSuspicious {

        @Test
        @DisplayName("느리면(threshold 이상) 위험 점수를 기록하지 않는다")
        void doesNotFlagWhenSlow() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("ticket:view:100:1")).willReturn(String.valueOf(System.currentTimeMillis() - 5000));
            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(listOperations.range("ticket:reaction-samples:100", 0, -1))
                    .willReturn(List.of("5000", "5200", "4900"));

            tracker.recordReserveAttemptAndScore(USER_ID, PERFORMANCE_ID);

            verify(valueOperations, never()).increment(anyString());
        }

        @Test
        @DisplayName("빠르지만 들쭉날쭉하면(CV 높음) 위험 점수를 기록하지 않는다")
        void doesNotFlagWhenInconsistent() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("ticket:view:100:1")).willReturn(String.valueOf(System.currentTimeMillis() - 100));
            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(listOperations.range("ticket:reaction-samples:100", 0, -1))
                    .willReturn(List.of("50", "400", "150"));

            tracker.recordReserveAttemptAndScore(USER_ID, PERFORMANCE_ID);

            verify(valueOperations, never()).increment(anyString());
        }

        @Test
        @DisplayName("표본 수가 minSamples 미만이면 위험 판정 자체를 안 한다")
        void doesNotFlagWhenNotEnoughSamples() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("ticket:view:100:1")).willReturn(String.valueOf(System.currentTimeMillis() - 100));
            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(listOperations.range("ticket:reaction-samples:100", 0, -1))
                    .willReturn(List.of("100", "102"));

            tracker.recordReserveAttemptAndScore(USER_ID, PERFORMANCE_ID);

            verify(valueOperations, never()).increment(anyString());
        }
    }

    @Nested
    @DisplayName("Redis 호출이 실패하면(fail-open)")
    class RedisUnavailable {

        @Test
        @DisplayName("조회 기록 실패해도 예외를 전파하지 않는다")
        void recordViewFailsSilently() {
            given(redisTemplate.opsForValue()).willThrow(new RedisConnectionFailureException("연결 실패"));

            tracker.recordView(USER_ID, PERFORMANCE_ID);
        }

        @Test
        @DisplayName("위험 판정 실패해도 예외를 전파하지 않는다 - hold 로직을 막으면 안 된다")
        void recordReserveAttemptFailsSilently() {
            given(redisTemplate.opsForValue()).willThrow(new RedisConnectionFailureException("연결 실패"));

            tracker.recordReserveAttemptAndScore(USER_ID, PERFORMANCE_ID);
        }
    }
}
