package com.programmers.kdt.ticket.risk;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * "공연 조회 → 좌석 hold 시도" 사이의 반응시간을 유저별로 누적해서, 평균이 비정상적으로 빠르고(절대속도)
 * 매번 일정한(변동계수 CV 낮음) 유저를 매크로 의심으로 표시한다. standby 매칭 시나리오는 이미 큐에서 뽑힌
 * 본인 1명에게만 배정돼서 "남보다 빨리 반응"할 대상 자체가 없어 후보에서 기각했고, 실제로 여러 유저가
 * 동시에 몰리는 지점(GENERAL 티켓 오픈 직후)에서만 이 신호가 의미를 가진다 - {@link TicketHoldServiceImpl}의
 * ticketOpenAt 게이트와 세트로 동작한다.
 * <p>
 * 위험 판정은 즉시 차단이 아니라 신호만 남긴다(gateway의 유저 단위 rate limit이 이 신호를 읽어서 허용치를
 * 동적으로 좁힘) - 이 부가 판단 자체가 실패해도 hold 로직 자체를 막으면 안 되므로 전부 fail-open으로 처리한다.
 */
@Slf4j
@Component
public class TicketReactionTracker {

    private static final String VIEW_KEY_PREFIX = "ticket:view:";
    private static final String SAMPLES_KEY_PREFIX = "ticket:reaction-samples:";
    private static final String RISK_KEY_PREFIX = "ticket:risk:";
    private static final int MAX_SAMPLES = 10;

    private final StringRedisTemplate redisTemplate;
    private final int minSamples;
    private final long speedThresholdMs;
    private final double cvThreshold;
    private final Duration viewTtl;
    private final Duration samplesTtl;
    private final Duration riskTtl;

    public TicketReactionTracker(
            StringRedisTemplate redisTemplate,
            @Value("${ticket.risk.min-samples}") int minSamples,
            @Value("${ticket.risk.speed-threshold-ms}") long speedThresholdMs,
            @Value("${ticket.risk.cv-threshold}") double cvThreshold,
            @Value("${ticket.risk.view-ttl-seconds}") long viewTtlSeconds,
            @Value("${ticket.risk.samples-ttl-seconds}") long samplesTtlSeconds,
            @Value("${ticket.risk.score-ttl-seconds}") long scoreTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.minSamples = minSamples;
        this.speedThresholdMs = speedThresholdMs;
        this.cvThreshold = cvThreshold;
        this.viewTtl = Duration.ofSeconds(viewTtlSeconds);
        this.samplesTtl = Duration.ofSeconds(samplesTtlSeconds);
        this.riskTtl = Duration.ofSeconds(scoreTtlSeconds);
    }

    /** GET /api/performances/{id}/sessions/seats 조회 시점을 기록한다. 로그인한 유저만 대상. */
    public void recordView(Long userId, Long performanceId) {
        try {
            redisTemplate.opsForValue().set(viewKey(userId, performanceId), String.valueOf(now()), viewTtl);
        } catch (Exception e) {
            log.warn("조회 시점 기록 실패 - fail-open으로 무시 (userId={}, performanceId={})", userId, performanceId, e);
        }
    }

    /**
     * 좌석 hold 시도 시점을 조회 시점과 비교해 반응시간 샘플을 누적하고, 위험 여부를 판정한다.
     * 조회 기록이 없으면(캐시된 페이지, 또는 조회 API를 안 거친 스크립트) 표본 부족으로 스킵한다 -
     * 오탐 방지를 우선한 판단이다.
     */
    public void recordReserveAttemptAndScore(Long userId, Long performanceId) {
        try {
            String viewKey = viewKey(userId, performanceId);
            String viewedAtRaw = redisTemplate.opsForValue().get(viewKey);
            if (viewedAtRaw == null) {
                return;
            }
            redisTemplate.delete(viewKey);

            long reactionMs = now() - Long.parseLong(viewedAtRaw);
            if (reactionMs < 0) {
                return;
            }

            List<Long> samples = appendSample(userId, reactionMs);
            if (samples.size() < minSamples) {
                return;
            }

            if (isSuspicious(samples)) {
                flagRisk(userId);
            }
        } catch (Exception e) {
            log.warn("반응시간 위험 판정 실패 - fail-open으로 무시 (userId={}, performanceId={})", userId, performanceId, e);
        }
    }

    private List<Long> appendSample(Long userId, long reactionMs) {
        String key = SAMPLES_KEY_PREFIX + userId;
        redisTemplate.opsForList().rightPush(key, String.valueOf(reactionMs));
        redisTemplate.opsForList().trim(key, -MAX_SAMPLES, -1);
        redisTemplate.expire(key, samplesTtl);

        List<String> raw = redisTemplate.opsForList().range(key, 0, -1);
        return raw == null ? List.of() : raw.stream().map(Long::parseLong).toList();
    }

    private boolean isSuspicious(List<Long> samples) {
        double mean = samples.stream().mapToLong(Long::longValue).average().orElse(0);
        if (mean == 0 || mean >= speedThresholdMs) {
            return false;
        }
        double variance = samples.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0);
        double cv = Math.sqrt(variance) / mean;
        return cv < cvThreshold;
    }

    private void flagRisk(Long userId) {
        String key = RISK_KEY_PREFIX + userId;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, riskTtl);
    }

    private String viewKey(Long userId, Long performanceId) {
        return VIEW_KEY_PREFIX + userId + ":" + performanceId;
    }

    private long now() {
        return System.currentTimeMillis();
    }
}
