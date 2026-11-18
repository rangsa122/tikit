import {apiClient} from "./apiClient.ts";
import type {ApiResponse} from "./apiError.ts";

export interface BookingStatusSummary{
    totalSeats: number
    soldSeats: number
    heldSeats: number
    bookingCount: number
    canceledCount: number
    revenue: number
}

export interface BookingStatusSession{
    sessionId: number
    startsAt: string
    totalSeats: number
    soldSeats: number
    heldSeats: number
    bookingCount: number
    canceledCount: number
    revenue: number
}

export interface PerformanceBookingStatus{
    performanceId: number
    summary: BookingStatusSummary
    sessions: BookingStatusSession[]
}

export async function getBookingStatus(
    performanceId: number,
): Promise<PerformanceBookingStatus>{
    const response = await apiClient.get<ApiResponse<PerformanceBookingStatus>>(
        `/api/v1/manager/performances/${performanceId}/booking-status`
    )
    if(response.data.code !== 'SUCCESS' || !response.data.data){
        throw new Error(response.data.message || '예매현황을 불러오지 못했습니다')
    }
    return response.data.data
}