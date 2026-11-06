package com.tikit.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "접근 권한이 없거나 현재 상태에서는 접근할 수 없습니다."
    ),
    ACCOUNT_RESTRICTED(
            HttpStatus.FORBIDDEN,
            "계정 이용이 제한되었습니다."
    ),
    ADMIN_ACCOUNT_PROTECTED(
            HttpStatus.FORBIDDEN,
            "관리자 계정은 이 API로 변경할 수 없습니다."
    ),
    ADMISSION_INVALID(
            HttpStatus.FORBIDDEN,
            "입장 권한이 없거나 만료되었습니다."
    ),
    AUTHENTICATION_REQUIRED(
            HttpStatus.UNAUTHORIZED,
            "인증이 필요합니다."
    ),
    BOOKING_ALREADY_CONFIRMED(
            HttpStatus.CONFLICT,
            "확정 예매는 전체 취소 API를 사용해야 합니다."
    ),
    BOOKING_EXPIRED(
            HttpStatus.CONFLICT,
            "결제 기한이 지났습니다."
    ),
    BOOKING_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "회차별 회원 매수 제한을 초과했습니다."
    ),
    CANCELLATION_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "다른 전체 취소 요청이 진행 중입니다."
    ),
    CANCELLATION_NOT_ALLOWED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "현재 예매 상태나 취소 기한에 따라 취소할 수 없습니다."
    ),
    CANCELLATION_QUOTE_CHANGED(
            HttpStatus.CONFLICT,
            "취소 금액 또는 정책이 변경되어 다시 확인해야 합니다."
    ),
    EMAIL_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "이미 사용 중인 이메일입니다."
    ),
    IDEMPOTENCY_CONFLICT(
            HttpStatus.CONFLICT,
            "동일한 멱등키로 다른 요청 내용을 전달할 수 없습니다."
    ),
    INDEX_NOT_READY(
            HttpStatus.CONFLICT,
            "해당 문서 버전의 색인이 완료되지 않았습니다."
    ),
    INVALID_BOOKING_STATE(
            HttpStatus.CONFLICT,
            "현재 예매 상태에서는 결제를 시작할 수 없습니다."
    ),
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "이메일 또는 비밀번호를 확인해 주세요."
    ),
    INVALID_PAYMENT_STATE(
            HttpStatus.CONFLICT,
            "현재 상태에서는 새로운 승인 요청을 시작할 수 없습니다."
    ),
    INVALID_SALES_CONFIGURATION(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "회차, 가격 또는 판매 일정 조건을 충족하지 못했습니다."
    ),
    KOPIS_ALREADY_REGISTERED(
            HttpStatus.CONFLICT,
            "이미 등록된 원본입니다."
    ),
    KOPIS_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "KOPIS 조회가 일시적으로 불가능합니다."
    ),
    NOT_MANAGER_ACCOUNT(
            HttpStatus.CONFLICT,
            "대상 계정이 MANAGER가 아닙니다."
    ),
    PASSWORD_MISMATCH(
            HttpStatus.UNAUTHORIZED,
            "비밀번호가 일치하지 않습니다."
    ),
    PAYMENT_AMOUNT_MISMATCH(
            HttpStatus.CONFLICT,
            "주문 또는 결제 금액이 DB와 일치하지 않습니다."
    ),
    PAYMENT_DECLINED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "PG 미승인 또는 거절이 최종 확인되었습니다."
    ),
    PAYMENT_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "기존 결제 승인 결과를 확인 중입니다. 잠시 후 다시 확인해 주세요."
    ),
    PERFORMANCE_ALREADY_PUBLISHED(
            HttpStatus.CONFLICT,
            "이미 게시된 공연입니다."
    ),
    PERFORMANCE_EDIT_LOCKED(
            HttpStatus.CONFLICT,
            "게시 후 또는 기존 예매가 있어 수정할 수 없습니다."
    ),
    PERFORMANCE_NOT_PUBLISHED(
            HttpStatus.CONFLICT,
            "게시된 공연만 공개 상태를 변경할 수 있습니다."
    ),
    PUBLISH_REQUIREMENTS_MISSING(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "회차, 좌석, 가격 또는 판매 기간 설정이 없습니다."
    ),
    RAG_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "AI 안내가 일시적으로 불가능합니다."
    ),
    RATE_LIMITED(
            HttpStatus.TOO_MANY_REQUESTS,
            "요청 횟수 제한을 초과했습니다. 잠시 후 다시 시도해 주세요."
    ),
    REFRESH_TOKEN_INVALID(
            HttpStatus.UNAUTHORIZED,
            "만료되었거나 이미 사용한 리프레시 토큰입니다."
    ),
    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "요청한 리소스를 찾을 수 없거나 접근할 수 없습니다."
    ),
    SALE_NOT_OPEN(
            HttpStatus.CONFLICT,
            "판매 시작 전이거나 판매가 종료되었습니다."
    ),
    SALE_RESUME_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "현재 게시 또는 공개 상태에서는 판매를 재개할 수 없습니다."
    ),
    SALE_STOPPED(
            HttpStatus.CONFLICT,
            "신규 판매가 중단되었습니다."
    ),
    SEAT_UNAVAILABLE(
            HttpStatus.CONFLICT,
            "선택한 좌석 중 선점 불가 좌석이 있습니다."
    ),
    SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "의존 서비스를 일시적으로 사용할 수 없습니다."
    ),
    STATE_CONFLICT(
            HttpStatus.CONFLICT,
            "현재 상태 또는 버전과 요청이 충돌합니다."
    ),
    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "요청 형식 또는 입력값을 확인해 주세요."
    ),
    VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "조회 이후 설정 버전이 변경되었습니다."
    ),
    WITHDRAWAL_BLOCKED(
            HttpStatus.CONFLICT,
            "진행 중 거래가 있어 탈퇴할 수 없습니다."
    );


    private final HttpStatus status;
    private final String message;
}
