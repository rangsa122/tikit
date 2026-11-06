import {apiClient} from "./apiClient.ts";
import {AUTH_API_PREFIX, authClient} from "./authSession";
import type {TokenResponse} from "./authSession";
import {useAuthStore} from "../stores/authStore";

interface LoginRequest {
    email: string
    password: string
}

export async function login(request: LoginRequest) {
    const response = await authClient.post<TokenResponse>(
        `${AUTH_API_PREFIX}/login`,
        request
    )

    return response.data
}

interface SignupRequest {
    email: string
    password: string
    name: string
}

interface SignupResponse {
    code: string
    message: string
    data: null
}

export async function signup(request: SignupRequest) {
    const response = await authClient.post<SignupResponse>(
        `${AUTH_API_PREFIX}/signup`,
        request
    );

    return response.data;
}

interface EmailAvailabilityResponse {
    code: string
    message: string
    data: { available: boolean }
}

export async function checkEmailAvailability(email: string) {
    const response = await authClient.get<EmailAvailabilityResponse>(
        `${AUTH_API_PREFIX}/email-availability`,
        {params: {email}}
    );

    return response.data.data.available;
}

export async function logout() {
    await apiClient.post(`${AUTH_API_PREFIX}/logout`, {});
    useAuthStore.getState().clearTokens();
}

