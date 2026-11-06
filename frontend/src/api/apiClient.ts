import axios from "axios";
import type {InternalAxiosRequestConfig} from "axios";
import {useAuthStore} from "../stores/authStore.ts";
import {getPendingTokenRefresh, refreshAccessToken} from "./authSession";

interface RetryRequestConfig extends InternalAxiosRequestConfig {
    _retry?: boolean;
}

export const apiClient = axios.create({
    withCredentials: true,
    timeout: 10000,
});

apiClient.interceptors.request.use(async config => {
    await getPendingTokenRefresh();
    const accessToken = useAuthStore.getState().tokens?.accessToken;

    if(accessToken) {
        config.headers.set('Authorization', `Bearer ${accessToken}`);
    } else {
        config.headers.delete('Authorization');
    }

    return config;
});

apiClient.interceptors.response.use(
    response => response,
    async (error: unknown) => {
        if (!axios.isAxiosError(error) || !error.config || error.response?.status !== 401) {
            return Promise.reject(error);
        }

        const config = error.config as RetryRequestConfig;
        const accessToken = useAuthStore.getState().tokens?.accessToken;
        const authorization = config.headers.get('Authorization');

        if (config._retry || !accessToken || !authorization) {
            return Promise.reject(error);
        }

        config._retry = true;
        const token = authorization === `Bearer ${accessToken}`
            ? await refreshAccessToken()
            : accessToken;

        config.headers.set('Authorization', `Bearer ${token}`);
        return apiClient.request(config);
    },
);
