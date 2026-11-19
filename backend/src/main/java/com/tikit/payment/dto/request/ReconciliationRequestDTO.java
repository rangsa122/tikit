package com.tikit.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 관리자 결제·환불 결과 재확인 요청 (사유는 저장 컬럼이 없어 로그와 멱등 해시에만 사용)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationRequestDTO {

    @NotBlank(message = "재확인 요청 사유는 필수 입력 항목입니다.")
    @Size(max = 1000, message = "재확인 요청 사유는 1000자 이내로 입력해야 합니다.")
    private String reason;
}