package com.programmers.kdt.ticket.service.impl;

import com.programmers.kdt.common.TimeLimits;
import com.programmers.kdt.common.constant.OrderTypeCode;
import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.exception.CommonErrorCode;
import com.programmers.kdt.common.util.TicketKeyGenerator;
import com.programmers.kdt.performance.entity.Performance;
import com.programmers.kdt.performance.repository.PerformanceRepository;
import com.programmers.kdt.ticket.cache.TicketZoneCacheStore;
import com.programmers.kdt.ticket.dto.CheckTicketHoldAvailableRequest;
import com.programmers.kdt.ticket.dto.CheckTicketHoldAvailableResponse;
import com.programmers.kdt.ticket.entity.Ticket;
import com.programmers.kdt.ticket.exception.TicketErrorCode;
import com.programmers.kdt.ticket.repository.TicketRepository;
import com.programmers.kdt.ticket.risk.TicketReactionTracker;
import com.programmers.kdt.ticket.service.TicketHoldService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TicketHoldServiceImpl implements TicketHoldService {

    private final TicketRepository ticketRepository;
    private final TicketZoneCacheStore ticketZoneCacheStore;
    private final PerformanceRepository performanceRepository;
    private final TicketReactionTracker ticketReactionTracker;

    @Override
    @Transactional
    public CheckTicketHoldAvailableResponse checkTicketHoldStatus(CheckTicketHoldAvailableRequest request, Long userId) {
        Ticket ticket = getTicketLock(request.ticketId());
        LocalDateTime holdExpiredAt = LocalDateTime.now().plusMinutes(TimeLimits.orderHoldTicket5Min);
        String holdKey = TicketKeyGenerator.generate();

        validateTicketConditionForHold(request.orderType(), ticket, userId);

        ticket.holdTicket(userId, holdExpiredAt, holdKey);
        ticketZoneCacheStore.markHold(ticket);

        if (OrderTypeCode.GENERAL.equals(request.orderType())) {
            ticketReactionTracker.recordReserveAttemptAndScore(userId, ticket.getPerformanceId());
        }

        return new CheckTicketHoldAvailableResponse(ticket.getTicketId(), ticket.getPrice(), holdExpiredAt, holdKey);
    }

    private void validateTicketConditionForHold(String orderType, Ticket ticket, Long userId) {
        switch (orderType) {
            case OrderTypeCode.GENERAL -> {
                validateTicketOpen(ticket.getPerformanceId());
                ticket.validateAvailableStatus();
            }
            case OrderTypeCode.STANDBY -> ticket.validateStandbyStatus(userId);
            default -> throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "주문 구분 값");
        }
    }

    // GENERAL 구매는 티켓 오픈 시각 이전에는 불가 - standby 매칭 구매(본인 전용 배정)는 오픈 시각과 무관해 대상 아님.
    private void validateTicketOpen(Long performanceId) {
        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new BusinessException(TicketErrorCode.TICKET_NOT_FOUND));
        LocalDateTime ticketOpenAt = performance.getTicketOpenAt();
        if (ticketOpenAt != null && LocalDateTime.now().isBefore(ticketOpenAt)) {
            throw new BusinessException(TicketErrorCode.TICKET_NOT_OPEN_YET);
        }
    }

    private @NonNull Ticket getTicketLock(Long ticketId) {
        return ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new BusinessException(TicketErrorCode.TICKET_NOT_FOUND));
    }
}