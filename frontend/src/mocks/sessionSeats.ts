
import type { SessionSeat } from "../api/sessionSeatApi";

// 실제 API 데이터가 없을 때 화면 개발에 사용하는 임시 좌석 데이터
export const mockSessionSeats: SessionSeat[] = Array.from(
    { length: 30 },
    (_, index) => {
        // 6개 좌석마다 다음 행으로 이동 (A~E)
        const row = Math.floor(index / 6);
        const column = index % 6;

        const rowLabel = String.fromCharCode(65 + row);
        const seatNumber = column + 1;

        return {
            seatId: index + 1,
            seatCode: `${rowLabel}${seatNumber}`,
            label: `${rowLabel}${seatNumber}`,
            rowLabel,
            seatNumber,

            // A, B열은 VIP / C, D, E열은 R등급
            grade: row < 2 ? "VIP" : "R",
            price: row < 2 ? 150000 : 100000,

            // 추후 좌석 배치도에서 사용할 좌표
            x: column * 60,
            y: row * 60,

            // 일부 좌석은 이미 판매된 상태로 설정
            status: [3, 8, 15, 20, 26].includes(index)
                ? "SOLD"
                : "AVAILABLE",
        };
    }
);
