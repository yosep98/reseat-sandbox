package com.programmers.kdt.filter;

import com.programmers.kdt.common.exception.CommonErrorCode;
import com.programmers.kdt.common.response.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traefik의 IP 단위 1차 rate limit과 별개로, 로그인한 유저 단위(JWT sub)로 요청 빈도를 제한한다.
 * IP 단위만으로는 "같은 유저가 매크로로 대기열(standby) 신청/취소를 반복"하는 패턴을 못 잡기 때문
 * (같은 IP라도 여러 계정을 돌리면 우회되고, NAT 뒤 여러 유저가 있으면 오탐이 생김)
 * JwtAuthFilterFunction 뒤에 붙어서, 이미 검증되어 헤더에 실린 X-User-Id를 카운터 키로 쓴다
 * - 토큰이 없는 요청(X-User-Id 미설정)은 대상이 아니므로 그대로 통과시킨다.
 * <p>
 * Sliding Window Log 방식: Redis ZSET(score=timestamp)에 요청을 개별 기록하고,
 * "윈도우 밖으로 나간 기록 제거 → 개수 확인 → 통과 시에만 기록 추가"를 Lua 스크립트로 원자화한다
 * (Fixed Window Counter와 달리 창 경계에서 설정치의 최대 2배까지 통과하는 버스트가 없음 - 정확한 횟수 제한이 목적).
 * <p>
 * 정합성이 핵심인 대기열 도메인 로직과 달리 이 rate limit은 매크로 방지용 부가 기능이므로,
 * Redis 장애 시에는 제한을 걸지 않고 요청을 통과시키는 fail-open으로 처리한다
 * (Redis가 죽었다고 해서 standby 신청/취소 자체가 막혀버리면 안 됨).
 */
public class UserRateLimitFilterFunction implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private static final Logger log = LoggerFactory.getLogger(UserRateLimitFilterFunction.class);

    private static final String SLIDING_WINDOW_LOG_SCRIPT =
            "local key = KEYS[1] "
                    + "local now = tonumber(ARGV[1]) "
                    + "local windowMillis = tonumber(ARGV[2]) "
                    + "local limit = tonumber(ARGV[3]) "
                    + "local member = ARGV[4] "
                    + "redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMillis) "
                    + "local count = redis.call('ZCARD', key) "
                    + "if count < limit then "
                    + "  redis.call('ZADD', key, now, member) "
                    + "  redis.call('PEXPIRE', key, windowMillis) "
                    + "  return 1 "
                    + "end "
                    + "return 0";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> slidingWindowLogScript;
    private final String keyPrefix;
    private final long windowMillis;
    private final int maxRequests;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public UserRateLimitFilterFunction(StringRedisTemplate redisTemplate, String keyPrefix, int windowSeconds, int maxRequests) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.windowMillis = windowSeconds * 1000L;
        this.maxRequests = maxRequests;
        this.slidingWindowLogScript = new DefaultRedisScript<>(SLIDING_WINDOW_LOG_SCRIPT, Long.class);
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        String userId = request.headers().firstHeader(JwtAuthFilterFunction.USER_ID_HEADER);
        if (userId == null) {
            return next.handle(request);
        }

        Long allowed = tryAcquire(userId);
        if (allowed == null || allowed == 1L) {
            return next.handle(request);
        }

        return tooManyRequests();
    }

    /** Redis 장애 시 null을 반환해 fail-open(통과)시킨다 - 매크로 방지는 부가 기능이지 핵심 정합성 요건이 아니다. */
    private Long tryAcquire(String userId) {
        String key = keyPrefix + userId;
        long now = System.currentTimeMillis();
        String member = now + "-" + UUID.randomUUID();

        try {
            return redisTemplate.execute(slidingWindowLogScript, List.of(key),
                    String.valueOf(now), String.valueOf(windowMillis), String.valueOf(maxRequests), member);
        } catch (Exception e) {
            log.warn("유저 단위 rate limit용 Redis 호출 실패 - fail-open으로 통과시킴 (userId={})", userId, e);
            return null;
        }
    }

    private ServerResponse tooManyRequests() {
        CommonErrorCode errorCode = CommonErrorCode.TOO_MANY_REQUESTS;
        ApiResponse<Void> body = ApiResponse.fail(errorCode.getCode(), errorCode.getMessage());
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonMapper.writeValueAsString(body));
    }
}
