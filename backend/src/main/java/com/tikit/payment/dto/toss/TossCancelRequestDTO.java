package com.tikit.payment.dto.toss;

import com.fasterxml.jackson.annotation.JsonInclude;

public record TossCancelRequestDTO(
        String cancelReason,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long cancelAmount
) {
}
