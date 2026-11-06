import {useEffect} from 'react';
import type {ReactNode} from 'react';
import {initializeAuth} from '../../api/authSession';
import {useAuthStore} from '../../stores/authStore';

export default function AuthProvider({children}: {children: ReactNode}) {
    const isInitialized = useAuthStore(state => state.isInitialized);

    useEffect(() => {
        void initializeAuth();
    }, []);

    if (!isInitialized) {
        return <p role="status" className="px-5 py-10">로그인 상태를 확인하고 있습니다.</p>;
    }

    return children;
}
