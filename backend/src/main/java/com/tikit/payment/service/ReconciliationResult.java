package com.tikit.payment.service;

import com.tikit.payment.dto.response.ReconciliationJobResponseDTO;

// 재확인 요청 응답 + 같은 멱등키 재요청 여부 (컨트롤러가 202/200을 나눔)
public record ReconciliationResult(ReconciliationJobResponseDTO response, boolean replay) {
}