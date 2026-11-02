package com.tikit.payment.service;

public enum ConfirmationStartStatus {
    STARTED,            // PROCESSING 저장 완료 → Toss 승인 호출
    ALREADY_APPROVED,   // 이미 승인됨 → Toss 호출 없이 200
    IN_PROGRESS         // 처리 중 또는 결과 확인 중 → Toss 호출 없이 202
}
