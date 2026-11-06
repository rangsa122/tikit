import {create} from "zustand/react";

interface Tokens {
    accessToken: string
}

interface AuthState {
    tokens: Tokens | null;
    isInitialized: boolean;
    setTokens: (tokens: Tokens) => void;
    clearTokens: () => void;
    finishInitialization: () => void;
}

export const useAuthStore = create<AuthState>()(set => ({
    tokens: null,
    isInitialized: false,
    setTokens: tokens => set({ tokens }),
    clearTokens: () => set({ tokens: null }),
    finishInitialization: () => set({ isInitialized: true }),
}));
