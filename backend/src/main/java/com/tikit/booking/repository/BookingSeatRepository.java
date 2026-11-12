package com.tikit.booking.repository;

import com.tikit.booking.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    // 예매에 담긴 좌석 목록
    List<BookingSeat> findByBookingId(Long bookingId);

    // 여러 예매의 좌석을 한 번에 조회 (목록 조회용)
    List<BookingSeat> findByBookingIdInOrderById(Collection<Long> bookingIds);
}
