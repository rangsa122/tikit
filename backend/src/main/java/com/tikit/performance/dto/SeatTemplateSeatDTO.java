package com.tikit.performance.dto;

import com.tikit.performance.entity.TemplateSeat;

public record SeatTemplateSeatDTO (String seatCode, String label, String grade,
                                   String row, int number, int x, int y
) {
    public static SeatTemplateSeatDTO from(TemplateSeat s) {
        return new SeatTemplateSeatDTO(s.getSeatCode(), s.getLabel(), s.getGrade(),
                s.getRowLabel(), s.getSeatNumber(), s.getX(), s.getY());
    }
}

