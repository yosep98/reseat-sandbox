package com.programmers.kdt.config;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.method;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import com.programmers.kdt.common.jwt.JwtProvider;
import com.programmers.kdt.filter.JwtAuthFilterFunction;
import com.programmers.kdt.filter.UserRateLimitFilterFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * properties 기반 spring.cloud.gateway.server.webmvc.routes 대신 Java RouterFunction으로 라우트를 정의한다.
 * JwtAuthFilterFunction(커스텀 HandlerFilterFunction)을 붙이려면 코드로 라우트를 만들어야 하기 때문.
 */
@Configuration
public class GatewayRouteConfig {

    @Value("${user-service.url}")
    private String userServiceUrl;

    @Value("${order-service.url}")
    private String orderServiceUrl;

    @Value("${performance-service.url}")
    private String performanceServiceUrl;

    @Value("${rate-limit.standby.window-seconds}")
    private int standbyRateLimitWindowSeconds;

    @Value("${rate-limit.standby.max-requests}")
    private int standbyRateLimitMaxRequests;

    @Bean
    public RouterFunction<ServerResponse> gatewayRoutes(JwtProvider jwtProvider, StringRedisTemplate redisTemplate) {
        JwtAuthFilterFunction jwtAuthFilterFunction = new JwtAuthFilterFunction(jwtProvider);
        UserRateLimitFilterFunction standbyRateLimitFilterFunction = new UserRateLimitFilterFunction(
                redisTemplate, "ratelimit:standby:user:", standbyRateLimitWindowSeconds, standbyRateLimitMaxRequests);

        RequestPredicate orderServicePaths = path("/api/order/**")
                .or(path("/api/payments/**"))
                .or(path("/api/points/**"))
                .or(path("/api/settlements/**"));

        RequestPredicate performanceServicePaths = path("/api/performances/**")
                .or(path("/api/performance/**"))
                .or(path("/api/v2/performances/**"))
                .or(path("/api/venues/**"))
                .or(path("/api/halls/**"))
                .or(path("/api/tickets/**"))
                .or(path("/api/standby/**"))
                .or(path("/api/images/**"))
                ;

        // 대기열(standby) 신청/취소 - 유저 단위 2차 rate limit 대상 (매크로로 순번을 싹쓸이하는 패턴 방지).
        // 조회(GET)는 대상이 아니므로, 아래 performanceServicePaths보다 먼저 매칭시켜 분리한다.
        RequestPredicate standbyMutationPaths = path("/api/standby").and(method(HttpMethod.POST))
                .or(path("/api/standby/**").and(method(HttpMethod.DELETE)));

        return route("performance-service-standby-mutation")
                .route(standbyMutationPaths, http())
                .before(uri(performanceServiceUrl))
                .filter(jwtAuthFilterFunction)
                .filter(standbyRateLimitFilterFunction)
                .build()
            .and(route("user-service")
                .route(path("/api/users/**"), http())
                .before(uri(userServiceUrl))
                .filter(jwtAuthFilterFunction)
                .build())
            .and(route("order-service")
                .route(orderServicePaths, http())
                .before(uri(orderServiceUrl))
                .filter(jwtAuthFilterFunction)
                .build())
            .and(route("performance-service")
                .route(performanceServicePaths, http())
                .before(uri(performanceServiceUrl))
                .filter(jwtAuthFilterFunction)
                .build());
    }
}
