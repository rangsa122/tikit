package com.tikit.notification.controller;

import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.notification.dto.NotificationSettingUpdateRequestDTO;
import com.tikit.notification.entity.OpeningSubscription;
import com.tikit.notification.service.OpeningSubscriptionService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


// 공연별 예매 시작 알림 및 전체 알림 설정 API
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class OpeningSubscriptionController {

    private final OpeningSubscriptionService subscriptionService;

    private Long getMemberId(String accountId) {
        if (accountId == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    "로그인이 필요합니다."
            );
        }

        return Long.valueOf(accountId);
    }

    // 공연별 예매 시작 알림 신청
    @PutMapping("/opening-subscriptions/{performanceId}")
    public ResponseEntity<ResponseDTO<Void>> subscribe(
            @AuthenticationPrincipal String accountId,
            @PathVariable Long performanceId
    ) {
        Long memberId = getMemberId(accountId);

        subscriptionService.subscribe(memberId, performanceId);

        return ResponseEntity.ok(
                ResponseDTO.success("알림 신청 성공")
        );
    }

    // 공연별 예매 시작 알림 <<해제>>
    @DeleteMapping("/opening-subscriptions/{performanceId}")
    public ResponseEntity<Void> unsubscribe(
            @AuthenticationPrincipal String accountId,
            @PathVariable Long performanceId
    ) {
        // 로그인 회원 ID 확인
        Long memberId = getMemberId(accountId);

        // 신청 기록 삭제 (없으면 정상 종료)
        subscriptionService.unsubscribe(memberId, performanceId);

        // 204 No Content 반환
        return ResponseEntity.noContent().build();
    }

    // 공연별 알림 신청 목록 조회
    @GetMapping("/opening-subscriptions")
    public ResponseEntity<ResponseDTO<Page<Long>>> getSubscriptions(
            @AuthenticationPrincipal String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long memberId = getMemberId(accountId);

        Page<OpeningSubscription> subscriptions =
                subscriptionService.getSubscriptions(
                        memberId, page, size
                );

        // 엔티티를 공연 ID 목록으로 변환
        Page<Long> result = subscriptions.map(
                OpeningSubscription::getPerformanceId
        );

        return ResponseEntity.ok(
                ResponseDTO.success("알림 신청 목록 조회 성공", result)
        );
    }


    /*      전체 알림 설정      */

    // 전체 알림 수신 설정 조회
    @GetMapping("/notification-preference")
    public ResponseEntity<ResponseDTO<Boolean>> getNotificationSetting(
            @AuthenticationPrincipal String accountId
    ) {
        Long memberId = getMemberId(accountId);

        boolean enabled =
                subscriptionService.getNotificationEnabled(memberId);

        return ResponseEntity.ok(
                ResponseDTO.success(
                        "전체 알림 수신 설정 조회 성공",
                        enabled
                )
        );
    }

    // 전체 알림 수신 설정 <<변경>>
    @PutMapping("/notification-preference")
    public ResponseEntity<ResponseDTO<?>> updateNotificationSetting(
            @AuthenticationPrincipal String accountId,
            @RequestBody NotificationSettingUpdateRequestDTO request
    ) {
        Long memberId = getMemberId(accountId);

        // 요청값 누락 검증
        if (request == null || request.enabled() == null) {
            ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

            return ResponseEntity
                    .status(errorCode.getStatus())
                    .body(ResponseDTO.fail(
                            errorCode,
                            "전체 알림 수신 설정값을 입력해 주세요."
                    ));
        }

        boolean enabled =
                subscriptionService.updateNotificationEnabled(
                        memberId,
                        request.enabled()
                );

        return ResponseEntity.ok(
                ResponseDTO.success(
                        "전체 알림 수신 설정 변경 성공",
                        enabled
                )
        );
    }
}
