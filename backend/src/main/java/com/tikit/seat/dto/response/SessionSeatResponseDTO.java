
package com.tikit.seat.dto.response;

public record SessionSeatResponseDTO(
        Long seatId,
        String seatCode,
        String label,
        String rowLabel,
        Integer seatNumber,
        String grade,
        Long price,
        Integer x,
        Integer y,
        String status,
        boolean heldByMe
) {
}
