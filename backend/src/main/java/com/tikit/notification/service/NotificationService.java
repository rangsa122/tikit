package com.tikit.notification.service;

import com.tikit.notification.dto.NotificationResponseDTO;
import com.tikit.notification.entity.Notification;
import com.tikit.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;


// 사이트 내부 알림 조회 및 읽음 처리
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // 내 알림 목록 조회
    public Page<NotificationResponseDTO> getNotifications(
            Long memberId,
            int page,
            int size,
            boolean unreadOnly
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "페이지 번호 또는 크기가 올바르지 않습니다."
            );
        }

        // 최신 알림부터 정렬
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<Notification> notifications;

        // 읽지 않은 알림만
        if (unreadOnly) {
            notifications =
                    notificationRepository.findByMemberIdAndReadAtIsNull(
                            memberId, pageable
                    );
        } else {
            // 전체 알림
            notifications =
                    notificationRepository.findByMemberId(
                            memberId, pageable
                    );
        }

    // 알림 목록의 예매 시작 일정 ID 수집
    List<Long> openingIds = notifications.getContent()
            .stream()
            .map(Notification::getOpeningId)
            .distinct()
            .toList();

    // 공연 제목 일괄 조회
    Map<Long, String> performanceTitles = getPerformanceTitles(openingIds);

    // 공연 제목을 포함한 DTO 반환

    return notifications.map(notification ->
            NotificationResponseDTO.from(
                    notification,
                    performanceTitles.get(notification.getOpeningId())
            )
        );
    }

    // 예매 시작 일정별 공연 제목 조회
    private Map<Long, String> getPerformanceTitles(
            List<Long> openingIds
    ) {
        Map<Long, String> titles = new HashMap<>();

        if (openingIds.isEmpty()) {
            return titles;
        }

        // 여러 공연 제목을 한 번에 조회
        var results = notificationRepository.findPerformanceTitlesByOpeningIds(openingIds);

        for (var result : results) {
            titles.put(result.getOpeningId(), result.getPerformanceTitle());
        }

        return titles;
    }

    // 내 알림 읽음 처리
    @Transactional
    public NotificationResponseDTO markAsRead(
            Long memberId,
            Long notificationId
    ) {
        // 내 알림만 조회
        Notification notification = notificationRepository
                .findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "알림을 찾을 수 없습니다."
                        )
                );

        notification.markAsRead();

        return NotificationResponseDTO.from(notification);
    }
}
