import {useState} from "react";
import type {ChangeEvent, SubmitEvent} from "react";
import {login} from "../../api/authApi.ts";
import * as axios from "axios";
import {Link, useNavigate} from "react-router";
import {useAuthStore} from "../../stores/authStore.ts";
import eyeIcon from "../../assets/auth/eye.svg";
import alertIcon from "../../assets/auth/alert.svg";

const INIT_LOGIN_FORM = {
    email: '',
    password: ''
}

const INIT_FIELD_ERRORS = {
    email: false,
    password: false
}

const INPUT_BORDER = 'border border-[#c9cdd4]'
const INPUT_BORDER_ERROR = 'border-[1.5px] border-[#b42318]'

export default function LoginPage() {

    const navigate = useNavigate();
    const setTokens = useAuthStore(state => state.setTokens);

    const [loginForm, setLoginForm] = useState(INIT_LOGIN_FORM);
    // 제출 중인지
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [message, setMessage] = useState('');
    // 오류 알림과 함께 빨간 테두리로 표시할 입력칸
    const [fieldErrors, setFieldErrors] = useState(INIT_FIELD_ERRORS);
    const [isPasswordVisible, setIsPasswordVisible] = useState(false);
    // 둘 다 입력하면 버튼 색을 바꿈. 회색일 때도 누르면 무엇이 빠졌는지 알림으로 안내
    const isFormFilled = !!loginForm.email.trim() && !!loginForm.password;

    function handleChange(e: ChangeEvent<HTMLInputElement>) {
        const {name, value} = e.currentTarget;

        setLoginForm(prev => ({
            ...prev,
            [name]: value,
        }))
        setFieldErrors(prev => ({
            ...prev,
            [name]: false,
        }))
    }

    function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
        e.preventDefault();

        if (isSubmitting) return;

        // 버튼은 막지 않고, 빈 칸이나 형식 오류를 알림으로 안내
        const form = e.currentTarget;
        const emailInput = form.elements.namedItem('email') as HTMLInputElement;
        const passwordInput = form.elements.namedItem('password') as HTMLInputElement;
        const isEmailEmpty = !loginForm.email.trim();
        const isPasswordEmpty = !loginForm.password;

        if (isEmailEmpty || isPasswordEmpty) {
            setMessage(isEmailEmpty && isPasswordEmpty
                ? '이메일과 비밀번호를 입력해 주세요.'
                : isEmailEmpty ? '이메일을 입력해 주세요.' : '비밀번호를 입력해 주세요.');
            setFieldErrors({email: isEmailEmpty, password: isPasswordEmpty});
            (isEmailEmpty ? emailInput : passwordInput).focus();
            return;
        }
        if (emailInput.validity.typeMismatch) {
            setMessage('이메일 형식을 확인해 주세요.');
            setFieldErrors({email: true, password: false});
            emailInput.focus();
            return;
        }

        setIsSubmitting(true);
        setMessage('');
        setFieldErrors(INIT_FIELD_ERRORS);

        login(loginForm)
            .then(res => {
                setTokens(res.data)
                setLoginForm(INIT_LOGIN_FORM);
                navigate('/', {replace: true});
            })
            .catch((err: unknown) => {
                if (axios.isAxiosError<{ code: string, message: string }>(err)) {
                    setMessage(
                        err.response?.data?.message ?? '서버에 연결할 수 없습니다.'
                    )
                    if (err.response?.data?.code === 'INVALID_CREDENTIALS') {
                        setFieldErrors({email: true, password: true});
                    }

                } else {
                    setMessage('로그인 처리 중 오류가 발생했습니다.')
                }
            })
            .finally(() => {
                setIsSubmitting(false);
            })
    }


    return (
        <form
            onSubmit={handleSubmit}
            noValidate
            aria-labelledby="login-title"
            className="mx-auto flex w-full max-w-[472px] flex-col gap-6 px-4 pt-24 pb-16 max-sm:pt-12">
            <div className="flex flex-col gap-1.5">
                <h1
                    id="login-title"
                    className="text-[28px] leading-[normal] font-bold text-[#111318]">
                    TIKIT 로그인
                </h1>
                <p className="text-[15px] leading-[1.5] text-[#5b616e]">
                    가입할 때 사용한 이메일과 비밀번호를 입력해 주세요.
                </p>
            </div>
            {message && (
                <div role="alert"
                     className="flex items-start gap-2.5 rounded-[10px] bg-[#fef3f2] px-4 py-3.5">
                    <img src={alertIcon} alt="" width={18} height={18} className="mt-0.5 shrink-0"/>
                    <p className="flex-1 text-sm leading-[1.5] font-medium text-[#b42318]">
                        {message}
                    </p>
                </div>
            )}
            <div className="flex flex-col gap-4">
                <label className="flex flex-col gap-2">
                    <span className="text-sm leading-[normal] font-medium text-[#111318]">이메일</span>
                    <input type="email"
                           name="email"
                           autoComplete="email"
                           placeholder="example@tikit.com"
                           disabled={isSubmitting}
                           onChange={handleChange}
                           value={loginForm.email}
                           aria-invalid={fieldErrors.email}
                           className={`h-[52px] w-full rounded-[10px] ${fieldErrors.email ? INPUT_BORDER_ERROR : INPUT_BORDER} bg-white px-4 text-base leading-[normal] text-[#111318] placeholder:text-[#8a909b] focus:outline-none!`}
                    />
                </label>
                <div className="flex flex-col gap-2">
                    <label htmlFor="login-password"
                           className="text-sm leading-[normal] font-medium text-[#111318]">
                        비밀번호
                    </label>
                    <div className={`flex h-[52px] w-full items-center gap-1 rounded-[10px] ${fieldErrors.password ? INPUT_BORDER_ERROR : INPUT_BORDER} bg-white pr-1.5 pl-4`}>
                        <input id="login-password"
                               type={isPasswordVisible ? 'text' : 'password'}
                               name="password"
                               autoComplete="current-password"
                               placeholder="비밀번호 입력"
                               disabled={isSubmitting}
                               onChange={handleChange}
                               value={loginForm.password}
                               aria-invalid={fieldErrors.password}
                               className="h-full min-w-0 flex-1 bg-transparent text-base leading-[normal] text-[#111318] placeholder:text-[#8a909b] focus:outline-none!"
                        />
                        <button
                            type="button"
                            onClick={() => setIsPasswordVisible(prev => !prev)}
                            aria-label={isPasswordVisible ? '비밀번호 숨기기' : '비밀번호 보기'}
                            aria-pressed={isPasswordVisible}
                            className="flex size-10 shrink-0 items-center justify-center rounded-[20px]">
                            <img src={eyeIcon} alt="" width={20} height={20}/>
                        </button>
                    </div>
                </div>
            </div>
            <button
                type="submit"
                disabled={isSubmitting}
                className={`h-[54px] w-full rounded-xl text-[17px] leading-[normal] font-bold transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${isFormFilled
                    ? 'bg-indigo-700 text-white hover:bg-indigo-800'
                    : 'bg-[#e4e6eb] text-[#5b616e]'}`}>
                {isSubmitting ? '로그인 중...' : '로그인'}
            </button>
            <button
                type="button"
                className="self-center text-sm leading-[normal] text-[#5b616e] underline">
                비밀번호를 잊으셨나요?
            </button>
            <div className="flex items-center gap-3">
                <div className="h-px flex-1 bg-[#e4e6eb]"/>
                <span className="text-[13px] leading-[normal] text-[#5b616e]">또는</span>
                <div className="h-px flex-1 bg-[#e4e6eb]"/>
            </div>
            <p className="flex justify-center gap-2 text-[15px] leading-[normal] text-[#5b616e]">
                아직 회원이 아니신가요?
                <Link to="/signup" className="font-bold text-indigo-700 underline">
                    회원가입
                </Link>
            </p>
        </form>
    )
}
