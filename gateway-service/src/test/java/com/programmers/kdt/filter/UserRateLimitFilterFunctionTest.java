package com.programmers.kdt.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("게이트웨이 유저 단위 rate limit 필터 (Sliding Window Log)")
class UserRateLimitFilterFunctionTest {

    private static final List<HttpMessageConverter<?>> CONVERTERS =
            List.of(new StringHttpMessageConverter(), new JacksonJsonHttpMessageConverter());
    private static final int WINDOW_SECONDS = 10;
    private static final int MAX_REQUESTS = 5;

    @Mock
    private StringRedisTemplate redisTemplate;

    private UserRateLimitFilterFunction filterFunction;

    @BeforeEach
    void setUp() {
        filterFunction = new UserRateLimitFilterFunction(redisTemplate, "ratelimit:standby:user:", WINDOW_SECONDS, MAX_REQUESTS);
    }

    private ServerRequest serverRequestWithUserId(String userId) {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/api/standby");
        if (userId != null) {
            mockRequest.addHeader(JwtAuthFilterFunction.USER_ID_HEADER, userId);
        }
        return ServerRequest.create(mockRequest, CONVERTERS);
    }

    private static final class CapturingHandler {
        private boolean invoked = false;

        ServerResponse handle(ServerRequest request) {
            this.invoked = true;
            return ServerResponse.ok().build();
        }
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면(비회원/미인증) Redis를 조회하지 않고 그대로 통과시킨다")
    void passesThroughWhenUserIdHeaderMissing() throws Exception {
        ServerRequest request = serverRequestWithUserId(null);
        CapturingHandler next = new CapturingHandler();

        filterFunction.filter(request, next::handle);

        assertThat(next.invoked).isTrue();
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString());
    }

    @Nested
    @DisplayName("윈도우 안의 기록 수가 아직 임계치 미만이면 (스크립트가 1을 반환)")
    class WithinLimit {

        @Test
        @DisplayName("통과시킨다")
        void passesThrough() throws Exception {
            given(redisTemplate.<Long>execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                    .willReturn(1L);
            ServerRequest request = serverRequestWithUserId("777");
            CapturingHandler next = new CapturingHandler();

            ServerResponse response = filterFunction.filter(request, next::handle);

            assertThat(next.invoked).isTrue();
            assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("윈도우 안의 기록 수가 이미 임계치에 도달했으면 (스크립트가 0을 반환)")
    class ExceedsLimit {

        @Test
        @DisplayName("다음 핸들러를 호출하지 않고 429를 반환한다")
        void rejectsWithTooManyRequests() throws Exception {
            given(redisTemplate.<Long>execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                    .willReturn(0L);
            ServerRequest request = serverRequestWithUserId("777");
            CapturingHandler next = new CapturingHandler();

            ServerResponse response = filterFunction.filter(request, next::handle);

            assertThat(next.invoked).isFalse();
            assertThat(response.statusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    @Nested
    @DisplayName("Redis 호출 자체가 실패하면 (fail-open)")
    class RedisUnavailable {

        @Test
        @DisplayName("제한을 걸지 않고 통과시킨다 - 매크로 방지 부가 기능 때문에 핵심 기능(standby)이 막히면 안 된다")
        void passesThroughOnRedisFailure() throws Exception {
            given(redisTemplate.<Long>execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                    .willThrow(new RedisConnectionFailureException("연결 실패"));
            ServerRequest request = serverRequestWithUserId("777");
            CapturingHandler next = new CapturingHandler();

            ServerResponse response = filterFunction.filter(request, next::handle);

            assertThat(next.invoked).isTrue();
            assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
