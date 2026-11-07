import axios from "axios";
import {BookingApiError} from "./bookingApi";
import {apiClient} from "./apiClient";

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
    // 내 입장 권한으로 잡아 둔 좌석
    heldByMe: boolean;
}

// 백엔드 공통 응답 형식
interface ApiResponse<T> {
    code: string;
    message: string;
    data: T;
}

// 공연 회차 ID로 좌석 목록 조회
export async function getSessionSeats(
    sessionId: number,
    admissionId?: string
): Promise<SessionSeat[]> {

    // 백엔드 좌석 조회 API 호출
    const response = await fetch(
        `/api/v1/sessions/${sessionId}/seats`,
        {headers: admissionId ? {"X-Admission-Id": admissionId} : undefined}
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

function seatHoldUrl(sessionId: number, seatId: number) {
    return `/api/v1/sessions/${sessionId}/seats/${seatId}/hold`;
}

async function requestSeatHold(call: () => Promise<unknown>) {
    try {
        await call();
    } catch (error) {
        if (axios.isAxiosError<{code?: string; message?: string}>(error)) {
            const body = error.response?.data;
            throw new BookingApiError(
                body?.code ?? "NETWORK_ERROR",
                body?.message ?? "서버에 연결하지 못했습니다. 다시 시도해 주세요."
            );
        }
        throw error;
    }
}

// 좌석을 누르는 순간 임시 선점. 이미 잡힌 좌석이면 SEAT_UNAVAILABLE
export async function holdSeat(sessionId: number, seatId: number, admissionId: string) {
    await requestSeatHold(() => apiClient.put(seatHoldUrl(sessionId, seatId), null, {
        headers: {"X-Admission-Id": admissionId},
    }));
}

// 임시 선점 해제
export async function releaseSeat(sessionId: number, seatId: number, admissionId: string) {
    await requestSeatHold(() => apiClient.delete(seatHoldUrl(sessionId, seatId), {
        headers: {"X-Admission-Id": admissionId},
    }));
}
