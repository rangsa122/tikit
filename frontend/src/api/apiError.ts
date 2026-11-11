import {AxiosError} from 'axios'

export interface ApiResponse<T> {
    code: string
    message: string
    data: T | null
}

export function toMessage(error: unknown, fallback: string): string {
    if (error instanceof AxiosError) {
        const data = error.response?.data as ApiResponse<unknown> | undefined
        if (data?.message) return data.message
    }
    if (error instanceof Error) return error.message
    return fallback
}