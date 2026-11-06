import type { SessionSeat } from "../api/sessionSeatApi";

const SEATS_PER_ROW = 20;
const ROW_LABELS = "ABCDEFGHIJKL".split("");

function gradeOf(rowIndex: number) {
    if (rowIndex < 3) {
        return {grade: "VIP", price: 170000};
    }
    return rowIndex < 8
        ? {grade: "R", price: 140000}
        : {grade: "A", price: 110000};
}

// 백엔드 없이 화면을 볼 때 쓰는 임시 좌석 (피그마 템플릿 A, 12열 20석)
export const mockSessionSeats: SessionSeat[] = ROW_LABELS.flatMap((rowLabel, rowIndex) =>
    Array.from({length: SEATS_PER_ROW}, (_, seatIndex) => {
        const seatNumber = seatIndex + 1;
        const id = rowIndex * SEATS_PER_ROW + seatNumber;

        return {
            seatId: id,
            seatCode: `${rowLabel}-${seatNumber}`,
            label: `${rowLabel}열 ${seatNumber}번`,
            rowLabel,
            seatNumber,
            ...gradeOf(rowIndex),
            x: seatIndex,
            y: rowIndex,
            // 일부 좌석은 이미 판매된 상태로 설정
            status: id % 7 === 3 ? "SOLD" : "AVAILABLE",
        };
    })
);
