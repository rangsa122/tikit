package com.tikit.payment.dto.request;

// 예매 전체 취소 요청 (견적에서 확인한 금액, 정책 버전을 함께 보내 서버 재계산 값과 비교)

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancellationCreateRequestDTO {

    @NotBlank(message = "취소 사유는 필수 입력 항목입니다.")
    @Size(max = 200, message = "취소 사유는 200자 이내로 입력해야 합니다.")
    private String reason;

    @NotNull(message = "예상 환불 금액은 필수 입력 항목입니다.")
    @Min(value = 0, message = "예상 환불 금액은 0원 이상이어야 합니다.")
    @Max(value = 100_000_000, message = "예상 환불 금액은 1억 원 이하여야 합니다.")
    private Long expectedRefundAmount;

    @NotBlank(message = "취소 정책 버전은 필수 입력 항목입니다.")
    @Size(max = 30, message = "취소 정책 버전은 30자 이내로 입력해야 합니다.")
    private String policyVersion;
}
