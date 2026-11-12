package com.tikit.booking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 예매 좌석 하나당 티켓 하나
    @Column(name = "booking_seat_id", nullable = false, unique = true)
    private Long bookingSeatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private TicketStatus status = TicketStatus.VALID;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    // 예매 확정 시 좌석별 티켓 발급
    public static Ticket issue(Long bookingSeatId, Instant now) {
        if (bookingSeatId == null) {
            throw new IllegalArgumentException("예매 좌석 ID는 필수입니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("발급 시각은 필수입니다.");
        }

        Ticket ticket = new Ticket();
        ticket.bookingSeatId = bookingSeatId;
        ticket.issuedAt = now;

        return ticket;
    }

    public boolean isValid() {
        return status == TicketStatus.VALID;
    }

    // 예매 취소 시 무효 처리 (VALID에서만 가능)
    public void voidTicket(Instant now) {
        if (status != TicketStatus.VALID) {
            throw new IllegalStateException("이미 무효 처리된 티켓입니다. ticketId=" + id);
        }
        this.status = TicketStatus.VOID;
        this.voidedAt = now;
    }
}
