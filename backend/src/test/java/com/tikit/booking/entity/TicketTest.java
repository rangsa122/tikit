package com.tikit.booking.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketTest {

    private static final Instant NOW = Instant.parse("2026-11-09T01:00:00Z");

    @Test
    @DisplayName("티켓은 VALID 상태와 발급 시각으로 발급된다")
    void issue() {
        Ticket ticket = Ticket.issue(1L, NOW);

        assertThat(ticket.getBookingSeatId()).isEqualTo(1L);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.VALID);
        assertThat(ticket.getIssuedAt()).isEqualTo(NOW);
        assertThat(ticket.getVoidedAt()).isNull();
        assertThat(ticket.isValid()).isTrue();
    }

    @Test
    @DisplayName("예매 좌석 ID나 발급 시각이 없으면 발급할 수 없다")
    void issueInvalid() {
        assertThatThrownBy(() -> Ticket.issue(null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ticket.issue(1L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("무효 처리하면 VOID가 되고 무효 처리 시각이 기록된다")
    void voidTicket() {
        Ticket ticket = Ticket.issue(1L, NOW);
        Instant voidedAt = NOW.plusSeconds(3600);

        ticket.voidTicket(voidedAt);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.VOID);
        assertThat(ticket.getVoidedAt()).isEqualTo(voidedAt);
        assertThat(ticket.isValid()).isFalse();
    }

    @Test
    @DisplayName("이미 무효 처리된 티켓은 다시 무효 처리할 수 없다")
    void voidTwice() {
        Ticket ticket = Ticket.issue(1L, NOW);
        ticket.voidTicket(NOW);

        assertThatThrownBy(() -> ticket.voidTicket(NOW)).isInstanceOf(IllegalStateException.class);
    }
}
