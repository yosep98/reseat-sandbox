package com.programmers.kdt.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.programmers.kdt.common.constant.OrderTypeCode;
import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.performance.entity.Performance;
import com.programmers.kdt.performance.repository.PerformanceRepository;
import com.programmers.kdt.ticket.cache.TicketZoneCacheStore;
import com.programmers.kdt.ticket.dto.CheckTicketHoldAvailableRequest;
import com.programmers.kdt.ticket.dto.CheckTicketHoldAvailableResponse;
import com.programmers.kdt.ticket.entity.Ticket;
import com.programmers.kdt.ticket.exception.TicketErrorCode;
import com.programmers.kdt.ticket.repository.TicketRepository;
import com.programmers.kdt.ticket.risk.TicketReactionTracker;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("좌석 hold 시도 - 티켓 오픈 게이트 + 반응속도 추적 연동")
class TicketHoldServiceImplTest {

    private static final Long PERFORMANCE_ID = 1L;
    private static final Long TICKET_ID = 1L;
    private static final Long USER_ID = 100L;

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketZoneCacheStore ticketZoneCacheStore;
    @Mock private PerformanceRepository performanceRepository;
    @Mock private TicketReactionTracker ticketReactionTracker;

    @InjectMocks private TicketHoldServiceImpl ticketHoldService;

    private static Ticket availableTicket() {
        Ticket ticket = Ticket.create(PERFORMANCE_ID, 1L, "A", "1", "1", 10000L);
        ReflectionTestUtils.setField(ticket, "ticketId", TicketHoldServiceImplTest.TICKET_ID);
        return ticket;
    }

    private static Performance performanceWithTicketOpenAt(LocalDateTime ticketOpenAt) {
        return Performance.createPerformance(
                "테스트 공연", "설명", 120L,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(10),
                ticketOpenAt, 999L, 1L, null);
    }

    @Nested
    @DisplayName("GENERAL 구매는")
    class GeneralPurchase {

        @Test
        @DisplayName("티켓 오픈 시각 이전이면 거부하고 반응속도 추적을 호출하지 않는다")
        void rejectsBeforeTicketOpen() {
            when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(availableTicket()));
            when(performanceRepository.findById(PERFORMANCE_ID))
                    .thenReturn(Optional.of(performanceWithTicketOpenAt(LocalDateTime.now().plusHours(1))));
            CheckTicketHoldAvailableRequest request = new CheckTicketHoldAvailableRequest(TICKET_ID, OrderTypeCode.GENERAL);

            assertThatThrownBy(() -> ticketHoldService.checkTicketHoldStatus(request, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(TicketErrorCode.TICKET_NOT_OPEN_YET.getMessage());
            verify(ticketReactionTracker, never()).recordReserveAttemptAndScore(any(), any());
        }

        @Test
        @DisplayName("티켓 오픈 시각 이후면 정상 처리되고 반응속도 추적을 호출한다")
        void allowsAfterTicketOpenAndTracksReaction() {
            when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(availableTicket()));
            when(performanceRepository.findById(PERFORMANCE_ID))
                    .thenReturn(Optional.of(performanceWithTicketOpenAt(LocalDateTime.now().minusMinutes(1))));
            CheckTicketHoldAvailableRequest request = new CheckTicketHoldAvailableRequest(TICKET_ID, OrderTypeCode.GENERAL);

            CheckTicketHoldAvailableResponse response = ticketHoldService.checkTicketHoldStatus(request, USER_ID);

            assertThat(response.ticketId()).isEqualTo(TICKET_ID);
            verify(ticketReactionTracker).recordReserveAttemptAndScore(USER_ID, PERFORMANCE_ID);
        }

        @Test
        @DisplayName("ticketOpenAt이 null(미설정)이면 게이트 없이 통과한다")
        void allowsWhenTicketOpenAtIsNull() {
            when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(availableTicket()));
            when(performanceRepository.findById(PERFORMANCE_ID))
                    .thenReturn(Optional.of(performanceWithTicketOpenAt(null)));
            CheckTicketHoldAvailableRequest request = new CheckTicketHoldAvailableRequest(TICKET_ID, OrderTypeCode.GENERAL);

            CheckTicketHoldAvailableResponse response = ticketHoldService.checkTicketHoldStatus(request, USER_ID);

            assertThat(response.ticketId()).isEqualTo(TICKET_ID);
        }
    }

    @Nested
    @DisplayName("STANDBY 구매(이미 매칭된 본인 전용)는")
    class StandbyPurchase {

        @Test
        @DisplayName("티켓 오픈 게이트와 무관하게 통과하고, 반응속도 추적은 호출하지 않는다")
        void skipsGateAndReactionTracking() {
            Ticket ticket = availableTicket();
            ticket.standbyTicket(USER_ID, LocalDateTime.now().plusMinutes(30));
            when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));
            CheckTicketHoldAvailableRequest request = new CheckTicketHoldAvailableRequest(TICKET_ID, OrderTypeCode.STANDBY);

            CheckTicketHoldAvailableResponse response = ticketHoldService.checkTicketHoldStatus(request, USER_ID);

            assertThat(response.ticketId()).isEqualTo(TICKET_ID);
            verify(performanceRepository, never()).findById(any());
            verify(ticketReactionTracker, never()).recordReserveAttemptAndScore(any(), any());
        }
    }
}
