package com.tikit.booking.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BookingCreateRequestDTO(
        @NotNull(message = "회차 ID는 필수입니다.")
        @Positive(message = "회차 ID는 1 이상이어야 합니다.")
        Long sessionId,

        @NotEmpty(message = "좌석을 1개 이상 선택해야 합니다.")
        @Size(max = 10, message = "좌석은 최대 10개까지 선택할 수 있습니다.")
        List<@NotNull @Positive Long> seatIds
) {
}
