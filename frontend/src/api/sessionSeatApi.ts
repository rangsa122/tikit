// 백엔드에서 전달받는 좌석 정보 타입
export interface SessionSeat {
    seatId: number;
    seatCode: string;
    label: string;
    rowLabel: string;
    seatNumber: number;
    grade: string;
    price: number;
    x: number;
    y: number;
    status: string;
}

// 백엔드 공통 응답 형식
interface ApiResponse<T> {
    code: string;
    message: string;
    data: T;
}

// 공연 회차 ID로 좌석 목록 조회
export async function getSessionSeats(
    sessionId: number
): Promise<SessionSeat[]> {

    // 백엔드 좌석 조회 API 호출
    const response = await fetch(
        `/api/v1/sessions/${sessionId}/seats`
    );

    // HTTP 오류 확인
   if (response.status === 404) {
       throw new Error("존재하지 않는 공연 회차입니다.");
   }

   if (!response.ok) {
       throw new Error("좌석 정보를 불러오지 못했습니다.");
   }

    // 응답 JSON을 좌석 목록으로 변환
    const result: ApiResponse<SessionSeat[]> = await response.json();

    return result.data;
}