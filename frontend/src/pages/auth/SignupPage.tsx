import {Link, useNavigate} from "react-router";
import {useRef, useState} from "react";
import type {ChangeEvent, FocusEvent, SubmitEvent} from "react";
import {checkEmailAvailability, signup} from "../../api/authApi.ts";
import {isAxiosError} from "axios";
import TermsModal from "../../components/auth/TermsModal.tsx";
import type {TermsTab} from "../../components/auth/TermsModal.tsx";
import eyeIcon from "../../assets/auth/eye.svg";
import alertIcon from "../../assets/auth/alert.svg";
import checkGrayIcon from "../../assets/auth/check-gray.svg";
import checkGreenIcon from "../../assets/auth/check-green.svg";
import checkWhiteIcon from "../../assets/auth/check-white.svg";


const INIT_SIGNUP_FORM = {
    email: '',
    name: '',
    password: '',
    passwordConfirm: '',
    termsAgreed: false,
};

const INIT_FIELD_ERRORS = {
    email: false,
    name: false,
    password: false,
    passwordConfirm: false,
    termsAgreed: false,
};

type FieldName = keyof typeof INIT_FIELD_ERRORS

const REQUIRED_FIELDS = [
    {name: 'email', label: '이메일'},
    {name: 'name', label: '이름'},
    {name: 'password', label: '비밀번호'},
    {name: 'passwordConfirm', label: '비밀번호 확인'},
] as const

const EMAIL_TAKEN_MESSAGE = '이미 가입된 이메일이에요.';
const EMAIL_FORMAT_MESSAGE = '이메일 형식을 확인해 주세요.';

const INPUT_BORDER = 'border border-[#c9cdd4]'
const INPUT_BORDER_ERROR = 'border-[1.5px] border-[#b42318]'
const INPUT_BORDER_SUCCESS = 'border-[1.5px] border-[#067647]'
const INPUT_TEXT = 'text-base leading-[normal] text-[#111318] placeholder:text-[#8a909b] focus:outline-none!'

// API 명세의 비밀번호 규칙: 10자 이상 128자 이하, 공백 없음 (128자는 maxLength로 막음)
function getPasswordRules(password: string) {
    return [
        {label: '10자 이상', passed: password.length >= 10},
        {label: '공백 없음', passed: password.length > 0 && !/\s/.test(password)},
    ];
}

// 마지막 글자에 받침이 있으면 '을', 없으면 '를'
function withObjectParticle(word: string) {
    const lastCode = word.charCodeAt(word.length - 1) - 0xAC00;
    return word + (lastCode % 28 === 0 ? '를' : '을');
}

export default function SignupPage() {

    const navigate = useNavigate();

    const [signupForm, setSignupForm] = useState(INIT_SIGNUP_FORM);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [message, setMessage] = useState('');
    // 오류 알림과 함께 빨간 테두리로 표시할 입력칸
    const [fieldErrors, setFieldErrors] = useState(INIT_FIELD_ERRORS);
    // 이메일 입력칸 아래 도움말 (형식 오류, 중복)
    const [emailHelper, setEmailHelper] = useState('');
    const [isPasswordVisible, setIsPasswordVisible] = useState(false);
    const [isPasswordConfirmVisible, setIsPasswordConfirmVisible] = useState(false);
    const [termsTab, setTermsTab] = useState<TermsTab | null>(null);
    // 늦게 도착한 중복 확인 응답이 바뀐 이메일에 적용되지 않도록 마지막 요청 값을 기억
    const latestCheckedEmail = useRef('');

    const passwordRules = getPasswordRules(signupForm.password);
    const isPasswordValid = passwordRules.every(rule => rule.passed);
    const isPasswordConfirmed = !!signupForm.passwordConfirm
        && signupForm.password === signupForm.passwordConfirm;
    // 모두 입력하고 약관에 동의하면 버튼 색을 바꿈. 회색일 때도 누르면 무엇이 빠졌는지 알림으로 안내
    const isFormFilled = REQUIRED_FIELDS.every(({name}) => !!signupForm[name].trim())
        && signupForm.termsAgreed;

    function clearFieldError(name: FieldName) {
        setFieldErrors(prev => ({
            ...prev,
            [name]: false,
        }));
    }

    function handleChange(e: ChangeEvent<HTMLInputElement>) {
        const {name, value, type, checked} = e.currentTarget;

        setSignupForm(prev => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value,
        }));
        clearFieldError(name as FieldName);

        if (name === 'email') {
            latestCheckedEmail.current = '';
            setEmailHelper('');
        }
    }

    // 이메일 입력칸을 벗어날 때 형식과 중복을 미리 확인
    function handleEmailBlur(e: FocusEvent<HTMLInputElement>) {
        const input = e.currentTarget;
        const email = signupForm.email.trim();

        if (!email) return;

        if (input.validity.typeMismatch) {
            setEmailHelper(EMAIL_FORMAT_MESSAGE);
            setFieldErrors(prev => ({...prev, email: true}));
            return;
        }

        latestCheckedEmail.current = email;

        checkEmailAvailability(email)
            .then(available => {
                if (latestCheckedEmail.current !== email || available) return;

                setEmailHelper(EMAIL_TAKEN_MESSAGE);
                setFieldErrors(prev => ({...prev, email: true}));
            })
            .catch(() => {
                // 확인에 실패해도 가입 요청에서 중복을 다시 검사하므로 입력은 막지 않음
            });
    }

    function handleTermsAgree() {
        setSignupForm(prev => ({...prev, termsAgreed: true}));
        clearFieldError('termsAgreed');
        setTermsTab(null);
    }

    function showError(text: string, errors: Partial<typeof INIT_FIELD_ERRORS>, focusTarget?: HTMLInputElement) {
        setMessage(text);
        setFieldErrors({...INIT_FIELD_ERRORS, ...errors});
        focusTarget?.focus();
    }

    function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
        e.preventDefault();

        if (isSubmitting) return;

        // 버튼은 막지 않고, 빈 칸이나 규칙 오류를 알림으로 안내
        const form = e.currentTarget;
        const getInput = (name: FieldName) => form.elements.namedItem(name) as HTMLInputElement;
        const emptyFields = REQUIRED_FIELDS.filter(({name}) => !signupForm[name].trim());

        if (emptyFields.length > 0) {
            showError(
                emptyFields.length === REQUIRED_FIELDS.length
                    ? '회원가입 정보를 입력해 주세요.'
                    : `${withObjectParticle(emptyFields.map(({label}) => label).join(', '))} 입력해 주세요.`,
                Object.fromEntries(emptyFields.map(({name}) => [name, true])),
                getInput(emptyFields[0].name)
            );
            return;
        }
        if (getInput('email').validity.typeMismatch) {
            setEmailHelper(EMAIL_FORMAT_MESSAGE);
            showError(EMAIL_FORMAT_MESSAGE, {email: true}, getInput('email'));
            return;
        }
        if (emailHelper === EMAIL_TAKEN_MESSAGE) {
            showError(`${EMAIL_TAKEN_MESSAGE} 다른 이메일을 입력해 주세요.`, {email: true}, getInput('email'));
            return;
        }
        if (!isPasswordValid) {
            showError('비밀번호는 공백 없이 10자 이상 입력해 주세요.', {password: true}, getInput('password'));
            return;
        }
        if (!isPasswordConfirmed) {
            showError('비밀번호가 일치하지 않아요.', {passwordConfirm: true}, getInput('passwordConfirm'));
            return;
        }
        if (!signupForm.termsAgreed) {
            showError('이용약관 및 개인정보처리방침에 동의해 주세요.', {termsAgreed: true}, getInput('termsAgreed'));
            return;
        }

        setIsSubmitting(true);
        setMessage('');
        setFieldErrors(INIT_FIELD_ERRORS);

        signup({
            email: signupForm.email.trim(),
            name: signupForm.name.trim(),
            password: signupForm.password
        })
            .then(() => {
                setSignupForm(INIT_SIGNUP_FORM);
                navigate('/login', {replace: true});
            })
            .catch((err: unknown) => {
                if (isAxiosError<{ code: string, message: string }>(err)) {
                    setMessage(
                        err.response?.data?.message ?? '서버에 연결할 수 없습니다.'
                    );
                    if (err.response?.data?.code === 'EMAIL_ALREADY_EXISTS') {
                        setEmailHelper(EMAIL_TAKEN_MESSAGE);
                        setFieldErrors(prev => ({...prev, email: true}));
                    }
                } else {
                    setMessage('회원가입 처리 중 오류가 발생했습니다.');
                }
            })
            .finally(() => {
                setIsSubmitting(false);
            })
    }

    function getPasswordConfirmBorder() {
        if (fieldErrors.passwordConfirm) return INPUT_BORDER_ERROR;
        if (!signupForm.passwordConfirm) return INPUT_BORDER;
        return isPasswordConfirmed ? INPUT_BORDER_SUCCESS : INPUT_BORDER_ERROR;
    }

    return (
        <>
            <form
                onSubmit={handleSubmit}
                noValidate
                aria-labelledby="signup-title"
                className="mx-auto flex w-full max-w-[472px] flex-col gap-6 px-4 pt-[72px] pb-16 max-sm:pt-12">
                <div className="flex flex-col gap-1.5">
                    <h1
                        id="signup-title"
                        className="text-[28px] leading-[normal] font-bold text-[#111318]">
                        TIKIT 회원가입
                    </h1>
                    <p className="text-[15px] leading-[1.5] text-[#5b616e]">
                        예매에 사용할 계정 정보를 입력해 주세요.
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
                    <div className="flex flex-col gap-2">
                        <label htmlFor="signup-email"
                               className="text-sm leading-[normal] font-medium text-[#111318]">
                            이메일
                        </label>
                        <input id="signup-email"
                               type="email"
                               name="email"
                               autoComplete="email"
                               maxLength={254}
                               placeholder="example@tikit.com"
                               disabled={isSubmitting}
                               onChange={handleChange}
                               onBlur={handleEmailBlur}
                               value={signupForm.email}
                               aria-invalid={fieldErrors.email}
                               aria-describedby={emailHelper ? 'signup-email-helper' : undefined}
                               className={`h-[52px] w-full rounded-[10px] ${fieldErrors.email ? INPUT_BORDER_ERROR : INPUT_BORDER} bg-white px-4 ${INPUT_TEXT}`}
                        />
                        {emailHelper && (
                            <p id="signup-email-helper" className="text-[13px] leading-[normal] text-[#b42318]">
                                {emailHelper}
                            </p>
                        )}
                    </div>
                    <div className="flex flex-col gap-2">
                        <label htmlFor="signup-name"
                               className="text-sm leading-[normal] font-medium text-[#111318]">
                            이름
                        </label>
                        <input id="signup-name"
                               type="text"
                               name="name"
                               autoComplete="name"
                               maxLength={50}
                               placeholder="이름 입력"
                               disabled={isSubmitting}
                               onChange={handleChange}
                               value={signupForm.name}
                               aria-invalid={fieldErrors.name}
                               className={`h-[52px] w-full rounded-[10px] ${fieldErrors.name ? INPUT_BORDER_ERROR : INPUT_BORDER} bg-white px-4 ${INPUT_TEXT}`}
                        />
                    </div>
                    <div className="flex flex-col gap-2.5">
                        <div className="flex flex-col gap-2">
                            <label htmlFor="signup-password"
                                   className="text-sm leading-[normal] font-medium text-[#111318]">
                                비밀번호
                            </label>
                            <div className={`flex h-[52px] w-full items-center gap-1 rounded-[10px] ${fieldErrors.password
                                ? INPUT_BORDER_ERROR
                                : isPasswordValid ? INPUT_BORDER_SUCCESS : INPUT_BORDER} bg-white pr-1.5 pl-4`}>
                                <input id="signup-password"
                                       type={isPasswordVisible ? 'text' : 'password'}
                                       name="password"
                                       autoComplete="new-password"
                                       maxLength={128}
                                       placeholder="비밀번호 입력"
                                       disabled={isSubmitting}
                                       onChange={handleChange}
                                       value={signupForm.password}
                                       aria-invalid={fieldErrors.password}
                                       aria-describedby="signup-password-rules"
                                       className={`h-full min-w-0 flex-1 bg-transparent ${INPUT_TEXT}`}
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
                        <ul id="signup-password-rules" className="flex gap-3.5">
                            {passwordRules.map(({label, passed}) => (
                                <li key={label}
                                    className={`flex items-center gap-1 text-[13px] leading-[normal] ${passed ? 'text-[#067647]' : 'text-[#5b616e]'}`}>
                                    <img src={passed ? checkGreenIcon : checkGrayIcon} alt="" width={14} height={14}/>
                                    {label}
                                    <span className="sr-only">{passed ? '충족' : '미충족'}</span>
                                </li>
                            ))}
                        </ul>
                    </div>
                    <div className="flex flex-col gap-2">
                        <label htmlFor="signup-password-confirm"
                               className="text-sm leading-[normal] font-medium text-[#111318]">
                            비밀번호 확인
                        </label>
                        <div className={`flex h-[52px] w-full items-center gap-1 rounded-[10px] ${getPasswordConfirmBorder()} bg-white pr-1.5 pl-4`}>
                            <input id="signup-password-confirm"
                                   type={isPasswordConfirmVisible ? 'text' : 'password'}
                                   name="passwordConfirm"
                                   autoComplete="new-password"
                                   maxLength={128}
                                   placeholder="비밀번호 다시 입력"
                                   disabled={isSubmitting}
                                   onChange={handleChange}
                                   value={signupForm.passwordConfirm}
                                   aria-invalid={fieldErrors.passwordConfirm}
                                   aria-describedby={signupForm.passwordConfirm ? 'signup-password-confirm-helper' : undefined}
                                   className={`h-full min-w-0 flex-1 bg-transparent ${INPUT_TEXT}`}
                            />
                            <button
                                type="button"
                                onClick={() => setIsPasswordConfirmVisible(prev => !prev)}
                                aria-label={isPasswordConfirmVisible ? '비밀번호 확인 숨기기' : '비밀번호 확인 보기'}
                                aria-pressed={isPasswordConfirmVisible}
                                className="flex size-10 shrink-0 items-center justify-center rounded-[20px]">
                                <img src={eyeIcon} alt="" width={20} height={20}/>
                            </button>
                        </div>
                        {signupForm.passwordConfirm && (
                            <p id="signup-password-confirm-helper"
                               className={`text-[13px] leading-[normal] ${isPasswordConfirmed ? 'text-[#067647]' : 'text-[#b42318]'}`}>
                                {isPasswordConfirmed ? '비밀번호가 일치해요.' : '비밀번호가 일치하지 않아요.'}
                            </p>
                        )}
                    </div>
                </div>
                <div className="flex items-center gap-2.5">
                    <input id="signup-terms"
                           type="checkbox"
                           name="termsAgreed"
                           checked={signupForm.termsAgreed}
                           onChange={handleChange}
                           disabled={isSubmitting}
                           aria-invalid={fieldErrors.termsAgreed}
                           className="peer sr-only"
                    />
                    <label htmlFor="signup-terms"
                           aria-hidden="true"
                           className={`flex size-[22px] shrink-0 cursor-pointer items-center justify-center rounded-[5px] peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-indigo-700 ${signupForm.termsAgreed
                               ? 'bg-indigo-700'
                               : `bg-white ${fieldErrors.termsAgreed ? 'border-[1.5px] border-[#b42318]' : 'border-[1.5px] border-[#9aa0aa]'}`}`}>
                        {signupForm.termsAgreed && <img src={checkWhiteIcon} alt="" width={14} height={14}/>}
                    </label>
                    <p className="text-sm leading-[normal] text-[#111318]">
                        <button type="button"
                                onClick={() => setTermsTab('terms')}
                                className="font-medium underline">
                            이용약관
                        </button>
                        {' 및 '}
                        <button type="button"
                                onClick={() => setTermsTab('privacy')}
                                className="font-medium underline">
                            개인정보처리방침
                        </button>
                        <label htmlFor="signup-terms" className="cursor-pointer">에 동의합니다 (필수)</label>
                    </p>
                </div>
                <button
                    type="submit"
                    disabled={isSubmitting}
                    className={`h-[54px] w-full rounded-xl text-[17px] leading-[normal] font-bold transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${isFormFilled
                        ? 'bg-indigo-700 text-white hover:bg-indigo-800'
                        : 'bg-[#e4e6eb] text-[#5b616e]'}`}>
                    {isSubmitting ? '가입 중...' : '가입하기'}
                </button>
                <p className="flex justify-center gap-2 text-[15px] leading-[normal] text-[#5b616e]">
                    이미 회원이신가요?
                    <Link to="/login" className="font-bold text-indigo-700 underline">
                        로그인
                    </Link>
                </p>
            </form>
            <TermsModal
                tab={termsTab}
                onTabChange={setTermsTab}
                onClose={() => setTermsTab(null)}
                onAgree={handleTermsAgree}
            />
        </>
    );
}
