import {apiClient} from "./apiClient.ts";
import {AxiosError} from "axios";

interface ApiResponse<T>{
    code: string
    message: string
    data: T|null
}

//좌석템플릿

export interface SeatTemplateSeat{
    seatCode: string
    label: string
    grade: string
    row: string
    number: number
    x: number
    y: number
}

export interface SeatTemplate{
    templateId: number
    name: string
    seats: SeatTemplateSeat[]
}

export async function getSeatTemplates():Promise<SeatTemplate[]> {
    const response = await apiClient.get<ApiResponse<{ items: SeatTemplate[] }>>(
        '/api/v1/manager/seat-templates',
    )
    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || '좌석 템플릿을 불러오지 못했습니다')
    }
    return response.data.data.items
}

//판매설정

export interface GradePrice{
    grade: string
    price: number
}

export interface SessionConfiguration{
    sessionId: number|null
    startsAt: string
    salesOpenAt: string
    salesCloseAt: string
    cancellationDeadline: string
    seatTemplateId: number
    gradePrices: GradePrice[]
}

export interface SalesConfiguration{
    performanceId: number
    version: number
    bookingLimitPerMember: number
    sessions: SessionConfiguration[]
}

export interface SalesConfigurationSaveRequest{
    expectedVersion: number
    bookingLimitPerMember: number
    sessions: SessionConfiguration[]
}

export async function getSalesConfiguration(
    performanceId: number,
): Promise<SalesConfiguration>{
    const response = await apiClient.get<ApiResponse<SalesConfiguration>>(
        `/api/v1/manager/performances/${performanceId}/sales-configuration`,
    )
    if(response.data.code !== 'SUCCESS' || !response.data.data){
        throw new Error(response.data.message || '판매설정을 불러오지 못했습니다')
    }
    return  response.data.data
}

export async function saveSalesConfiguration(
    performanceId: number,
    request: SalesConfigurationSaveRequest,
): Promise<SalesConfiguration>{
    const response = await apiClient.put<ApiResponse<SalesConfiguration>>(
        `/api/v1/manager/performances/${performanceId}/sales-configuration`,
        request,
    )
    if(response.data.code !== 'SUCCESS' || !response.data.data){
        throw new Error(response.data.message || '판매 설정을 저장하지 못했습니다')
    }
    return response.data.data
}

// ===== 시각 변환 =====
// 서버는 OffsetDateTime("2026-12-01T19:00:00+09:00")을 주고받지만
// <input type="datetime-local">은 "2026-12-01T19:00" 형식만 다룬다

const KST_OFFSET = '+09:00'

export function toOffsetDateTime(localValue: string): string {
    const withSeconds = localValue.length === 16 ? `${localValue}:00` : localValue
    return `${withSeconds}${KST_OFFSET}`
}

export function toLocalInput(offsetValue: string): string {
    return offsetValue.slice(0, 16)
}

// 서버가 보낸 message를 꺼낸다. axios는 4xx·5xx에서 예외를 던지므로
// 그 안에 담긴 응답 본문을 봐야 한다.
export function toMessage(error: unknown, fallback: string): string {
    if (error instanceof AxiosError) {
        const data = error.response?.data as ApiResponse<unknown> | undefined
        if (data?.message) return data.message
    }
    if (error instanceof Error) return error.message
    return fallback
}