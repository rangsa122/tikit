package com.tikit.booking.service;

import com.tikit.booking.dto.response.BookingResponseDTO;

// created가 false면 같은 입장 ID로 이미 만든 예매를 돌려준 것
public record BookingResult(BookingResponseDTO booking, boolean created) {
}
