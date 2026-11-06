package com.tikit.queue.service;

import com.tikit.booking.repository.SeatHoldRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PerformanceSession;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.entity.SalesStatus;
import com.tikit.performance.entity.Visibility;
import com.tikit.queue.exception.QueueException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// 줄 서기 전에 회차가 지금 판매 중인지 DB로 확인 (예매 생성과 같은 기준)
@Component
@RequiredArgsConstructor
public class QueueSaleValidator {

    private final SeatHoldRepository seatHoldRepository;

    @Transactional(readOnly = true)
    public void validateOnSale(Long sessionId, Instant now) {
        PerformanceSession session = seatHoldRepository.findSessionWithPerformance(sessionId)
                .orElseThrow(() -> new QueueException(ErrorCode.RESOURCE_NOT_FOUND));
        Performance performance = session.getPerformance();

        // 공개되지 않은 공연의 회차는 없는 것처럼 404
        if (performance.getPublicationStatus() != PublicationStatus.PUBLISHED
                || performance.getVisibility() != Visibility.PUBLIC) {
            throw new QueueException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (performance.getSalesStatus() != SalesStatus.ENABLED) {
            throw new QueueException(ErrorCode.SALE_STOPPED);
        }
        Instant opensAt = seatHoldRepository.findOpensAt(session.getOpeningId())
                .orElseThrow(() -> new QueueException(ErrorCode.SALE_NOT_OPEN));
        if (now.isBefore(opensAt) || !now.isBefore(session.getSalesCloseAt())) {
            throw new QueueException(ErrorCode.SALE_NOT_OPEN);
        }
    }
}
