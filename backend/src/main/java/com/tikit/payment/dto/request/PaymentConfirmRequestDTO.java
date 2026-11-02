package com.tikit.payment.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmRequestDTO {

    @NotBlank(message = "결제 키는 필수 입력 항목입니다.")
    @Size(max = 200, message = "결제 키는 200자 이내로 입력해야 합니다.")
    private String paymentKey;

    @NotBlank(message = "주문 번호는 필수 입력 항목입니다.")
    @Pattern(regexp = "^[A-Za-z0-9_-]{6,64}$", message = "주문 번호 형식이 올바르지 않습니다.")
    private String orderId;

    @NotNull(message = "결제 금액은 필수 입력 항목입니다.")
    @Min(value = 0, message = "결제 금액은 0원 이상이어야 합니다.")
    @Max(value = 100_000_000, message = "결제 금액은 1억 원 이하여야 합니다.")
    private Long amount;

}
