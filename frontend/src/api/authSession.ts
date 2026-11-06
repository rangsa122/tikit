import axios from 'axios';
import {useAuthStore} from '../stores/authStore';

export const AUTH_API_PREFIX = '/api/v1/auth';

export const authClient = axios.create({
    withCredentials: true,
    timeout: 10000,
});

export interface TokenResponse {
    code: string;
    message: string;
    data: { accessToken: string };
}

let refreshPromise: Promise<string> | null = null;
let initializationPromise: Promise<void> | null = null;

export function getPendingTokenRefresh() {
    return refreshPromise;
}

export function refreshAccessToken(): Promise<string> {
    if (refreshPromise) return refreshPromise;

    const request = async () => {
        const response = await authClient.post<TokenResponse>(`${AUTH_API_PREFIX}/refresh`, {});
        const {accessToken} = response.data.data;
        useAuthStore.getState().setTokens({accessToken});
        return accessToken;
    };

    let requestPromise: Promise<string>;

    if (navigator.locks) {
        requestPromise = navigator.locks.request('tikit-auth-refresh', request);
    } else {
        requestPromise = request();
    }

    refreshPromise = requestPromise
        .catch((error: unknown) => {
            if (axios.isAxiosError(error) && error.response?.status === 401) {
                useAuthStore.getState().clearTokens();
            }
            throw error;
        })
        .finally(() => {
            refreshPromise = null;
        });

    return refreshPromise;
}

export function initializeAuth(): Promise<void> {
    if (!initializationPromise) {
        try {
            sessionStorage.removeItem('tikit-auth');
        } catch {
            console.warn('이전 인증 저장 정보를 정리하지 못했습니다.');
        }

        initializationPromise = refreshAccessToken()
            .then(() => undefined, () => undefined)
            .finally(() => useAuthStore.getState().finishInitialization());
    }

    return initializationPromise;
}
