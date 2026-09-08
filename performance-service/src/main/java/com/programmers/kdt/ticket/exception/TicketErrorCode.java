package com.programmers.kdt.ticket.exception;

import com.programmers.kdt.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TicketErrorCode implements ErrorCode {
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND,"TKT404_001", "존재하지 않는 티켓입니다. {0}"),
    TICKET_ISSUE_FAILED(HttpStatus.CONFLICT, "TKT409_001", "티켓 발행 중 에러가 발생했습니다."),
    IMPOSSIBLE_HOLD_TICKET(HttpStatus.UNPROCESSABLE_CONTENT, "TKT422_001", "이미 점유된 상태입니다."),
    HOLD_TICKET_NOT_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "TKT422_002", "티켓 점유 만료 시간이 지나지 않았습니다."),
    HOLD_KEY_DISCREPANCY(HttpStatus.UNPROCESSABLE_CONTENT, "TKT422_003", "티켓 점유 만료 시간이 지나지 않았습니다."),
    TICKET_PRICE_DISCREPANCY(HttpStatus.UNPROCESSABLE_CONTENT, "TKT422_005", "티켓 금액이 일치하지 않습니다."),
    STANDBY_TICKET_DISCREPANCY(HttpStatus.UNPROCESSABLE_CONTENT, "TKT422_004", "대기표에 대한 접근 권한이 없습니다."),
    NOT_RESERVED_TICKET(HttpStatus.UNPROCESSABLE_CONTENT, "TKT422_006", "예매되지 않은 티켓입니다."),
    TICKET_OWNER_DISCREPANCY(HttpStatus.UNPROCESSABLE_CONTENT, "TKT422_006", "티켓 구매자가 아닙니다."),
    TICKET_IS_NOT_HELD(HttpStatus.UNPROCESSABLE_CONTENT, "TKT422_007", "점유되지 않은 티켓입니다."),
    HOLD_TICKET_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "TKT422_008", "티켓 점유 시간이 만료되어 결제할 수 없습니다."),
    ALREADY_RESERVED_TICKET(HttpStatus.UNPROCESSABLE_CONTENT, "TKT422_000", "이미 결제 완료된 티켓입니다."),
    TICKET_NOT_OPEN_YET(HttpStatus.UNPROCESSABLE_CONTENT, "TKT422_009", "아직 티켓 오픈 시각이 되지 않았습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
