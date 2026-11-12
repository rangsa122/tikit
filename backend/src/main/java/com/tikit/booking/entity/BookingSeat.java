package com.tikit.booking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EntityListeners(AuditingEntityListener.class)
@Table(name = "booking_seats")
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    // 예매와 좌석이 같은 회차인지 FK(booking_id, session_id)로 보장
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "session_seat_id", nullable = false)
    private Long sessionSeatId;

    // 예매 시점 좌석 표기 (예: A열 12번)
    @Column(name = "seat_label_snapshot", nullable = false, length = 40)
    private String seatLabelSnapshot;

    @Column(name = "grade_snapshot", nullable = false, length = 20)
    private String gradeSnapshot;

    @Column(name = "price_snapshot", nullable = false)
    private Long priceSnapshot;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 예매에 담길 좌석 생성 (좌석 정보와 가격은 예매 시점 값으로 고정)
    public static BookingSeat create(Long bookingId,
                                     Long sessionId,
                                     Long sessionSeatId,
                                     String seatLabelSnapshot,
                                     String gradeSnapshot,
                                     Long priceSnapshot) {
        if (bookingId == null || sessionId == null || sessionSeatId == null) {
            throw new IllegalArgumentException(
                    "예매 ID, 회차 ID, 회차 좌석 ID는 필수입니다. bookingId=" + bookingId
                            + ", sessionId=" + sessionId + ", sessionSeatId=" + sessionSeatId);
        }
        if (priceSnapshot == null || priceSnapshot < 0) {
            throw new IllegalArgumentException("좌석 가격은 0 이상이어야 합니다. priceSnapshot=" + priceSnapshot);
        }

        BookingSeat bookingSeat = new BookingSeat();
        bookingSeat.bookingId = bookingId;
        bookingSeat.sessionId = sessionId;
        bookingSeat.sessionSeatId = sessionSeatId;
        bookingSeat.seatLabelSnapshot = seatLabelSnapshot;
        bookingSeat.gradeSnapshot = gradeSnapshot;
        bookingSeat.priceSnapshot = priceSnapshot;

        return bookingSeat;
    }
}
