package com.programmers.kdt.common.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COM400_001", "잘못된 요청입니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COM400_002", "{0} 입력값이 올바르지 않습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "COM400_003", "{0} 요청 데이터 검증에 실패했습니다."),
    PAGE_BAD_REQUEST(HttpStatus.BAD_REQUEST, "COM400_004", "유효하지 않은 페이지 값입니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "COM400_005", "조회 시작일은 종료일보다 이후일 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COM401_001", "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "C401_002", "유효하지 않은 인증 정보입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COM403_001", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COM404_001", "요청한 리소스를 찾을 수 없습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COM429_001", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COM500_001", "서버 오류가 발생했습니다."),
    BAD_GATEWAY(HttpStatus.BAD_GATEWAY, "COM502_001", "외부 서비스 호출 중 오류가 발생했습니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "COM503_001", "서비스를 일시적으로 이용할 수 없습니다."),
    GATEWAY_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "COM504_001", "외부 서비스 응답이 지연되고 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    CommonErrorCode(HttpStatus httpStatus, String code, String message) {
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
