package com.tikit.payment.service;

import com.tikit.payment.dto.response.CancellationResponseDTO;

// 예매 전체 취소 결과 (replay면 같은 키로 이미 처리된 요청 -> 컨트롤러가 200, 새 요청이면 202)
public record CancellationResult(CancellationResponseDTO response, boolean replay) {
}