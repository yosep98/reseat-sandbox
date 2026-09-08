package com.programmers.kdt.recommendation.exception;

import com.programmers.kdt.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum RecommendationErrorCode implements ErrorCode {

    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "REC503_001", "추천 서비스를 일시적으로 이용할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    RecommendationErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
