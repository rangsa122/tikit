import {useEffect, useRef} from "react";
import type {MouseEvent} from "react";
import closeIcon from "../../assets/auth/close.svg";

export type TermsTab = 'terms' | 'privacy'

const TABS: { key: TermsTab, label: string }[] = [
    {key: 'terms', label: '이용약관'},
    {key: 'privacy', label: '개인정보처리방침'},
]

const TERMS_SECTIONS = [
    {
        title: '제1조 (목적)',
        paragraphs: ['이 약관은 TIKIT이 제공하는 공연 티켓 예매 서비스의 이용 조건과 절차, 회원과 서비스의 권리와 의무를 정합니다.'],
    },
    {
        title: '제2조 (회원가입)',
        paragraphs: [
            '이메일, 이름, 비밀번호를 입력하고 이 약관에 동의하면 회원가입이 완료됩니다.',
            '[가입 제한 사유 등 세부 내용]',
        ],
    },
    {
        title: '제3조 (예매와 결제)',
        paragraphs: [
            '예매는 좌석 선택 후 제한시간 안에 결제를 마쳐야 확정됩니다. 제한시간이 지나면 선택한 좌석은 자동으로 해제됩니다.',
            '[결제 수단, 결제 오류 처리 등 세부 내용]',
        ],
    },
    {
        title: '제4조 (취소와 환불)',
        paragraphs: [
            '예매는 전체 취소만 가능하며, 취소 수수료와 환불 기준은 공연별 안내를 따릅니다.',
            '[취소 마감, 수수료 기준 등 세부 내용]',
        ],
    },
    {
        title: '제5조 (회원 탈퇴)',
        paragraphs: ['관람 전인 유효한 예매가 있으면 탈퇴할 수 없습니다. [세부 내용]'],
    },
]

const PRIVACY_ROWS = [
    ['이메일, 이름, 비밀번호', '회원 식별, 로그인', '회원 탈퇴 시까지'],
    ['예매 내역, 결제 정보', '예매 확인, 결제와 환불 처리', '[관련 법령에 따른 기간]'],
    ['알림 신청 내역', '예매 시작 알림 발송', '회원 탈퇴 시까지'],
]

const PRIVACY_SECTIONS = [
    {
        title: '동의를 거부할 권리',
        paragraphs: ['개인정보 수집과 이용에 동의하지 않을 수 있습니다. 다만 동의하지 않으면 회원가입과 예매 서비스를 이용할 수 없습니다.'],
    },
    {
        title: '개인정보 처리 위탁',
        paragraphs: ['결제 처리를 위해 토스페이먼츠에 결제 정보를 위탁합니다. [위탁 범위 세부 내용]'],
    },
]

interface TermsModalProps {
    // null이면 닫힌 상태
    tab: TermsTab | null
    onTabChange: (tab: TermsTab) => void
    onClose: () => void
    onAgree: () => void
}

export default function TermsModal({tab, onTabChange, onClose, onAgree}: TermsModalProps) {

    const dialogRef = useRef<HTMLDialogElement>(null);
    const scrollRef = useRef<HTMLDivElement>(null);
    const isOpen = tab !== null;

    // showModal로 열어야 ESC 닫기, 포커스 가두기, 배경 클릭 막기가 브라우저 기본 동작으로 처리됨
    useEffect(() => {
        const dialog = dialogRef.current;
        if (!dialog) return;

        if (isOpen && !dialog.open) dialog.showModal();
        if (!isOpen && dialog.open) dialog.close();
    }, [isOpen]);

    useEffect(() => {
        scrollRef.current?.scrollTo({top: 0});
    }, [tab]);

    function handleBackdropClick(e: MouseEvent<HTMLDialogElement>) {
        if (e.target === e.currentTarget) onClose();
    }

    return (
        <dialog
            ref={dialogRef}
            onClose={onClose}
            onClick={handleBackdropClick}
            aria-labelledby="terms-title"
            className="m-auto w-[640px] max-w-[calc(100%-32px)] overflow-hidden rounded-[20px] bg-white p-0 shadow-[0px_12px_40px_0px_rgba(0,0,0,0.18)] backdrop:bg-[rgba(17,19,24,0.55)]">
            <div className="flex items-center justify-between pt-5 pr-4 pb-2 pl-7 max-sm:pl-5">
                <h2 id="terms-title" className="text-xl leading-[normal] font-bold text-[#111318]">
                    약관 보기
                </h2>
                <button
                    type="button"
                    onClick={onClose}
                    aria-label="닫기"
                    className="flex size-11 items-center justify-center rounded-[22px]">
                    <img src={closeIcon} alt="" width={22} height={22}/>
                </button>
            </div>
            <div role="tablist" className="flex border-b border-[#e4e6eb] px-7 max-sm:px-5">
                {TABS.map(({key, label}) => (
                    <button
                        key={key}
                        type="button"
                        role="tab"
                        id={`terms-tab-${key}`}
                        aria-selected={tab === key}
                        aria-controls="terms-panel"
                        onClick={() => onTabChange(key)}
                        className={`-mb-px px-4 pt-3.5 pb-3 text-[15px] leading-[normal] ${tab === key
                            ? 'border-b-2 border-[#4338ca] font-bold text-[#111318]'
                            : 'text-[#5b616e]'}`}>
                        {label}
                    </button>
                ))}
            </div>
            <div
                ref={scrollRef}
                id="terms-panel"
                role="tabpanel"
                aria-labelledby={`terms-tab-${tab ?? 'terms'}`}
                tabIndex={0}
                className="flex h-[480px] max-h-[calc(100dvh-260px)] flex-col gap-[22px] overflow-y-auto py-6 pr-6 pl-7 [scrollbar-color:#c9cdd4_#f2f4f7] [scrollbar-width:thin] max-sm:px-5">
                <p className="text-[13px] leading-[normal] text-[#5b616e]">시행일: [시행일]</p>
                {tab === 'privacy' ? (
                    <>
                        <p className="text-sm leading-[1.65] text-[#3a3f48]">
                            TIKIT은 회원가입과 예매 서비스 제공을 위해 아래와 같이 개인정보를 수집하고 이용합니다.
                        </p>
                        <table className="w-full table-fixed border-separate border-spacing-0 overflow-hidden rounded-[10px] border border-[#e4e6eb] text-left text-[13px] leading-[1.5]">
                            <thead className="bg-[#f6f7f9] text-[#111318]">
                            <tr>
                                {['수집 항목', '이용 목적', '보유 기간'].map(head => (
                                    <th key={head} className="border-b border-[#e4e6eb] px-3.5 py-3 font-bold">
                                        {head}
                                    </th>
                                ))}
                            </tr>
                            </thead>
                            <tbody className="text-[#3a3f48]">
                            {PRIVACY_ROWS.map((row, rowIndex) => (
                                <tr key={row[0]}>
                                    {row.map(cell => (
                                        <td key={cell}
                                            className={`px-3.5 py-3 align-top ${rowIndex < PRIVACY_ROWS.length - 1 ? 'border-b border-[#e4e6eb]' : ''}`}>
                                            {cell}
                                        </td>
                                    ))}
                                </tr>
                            ))}
                            </tbody>
                        </table>
                        <TermsSections sections={PRIVACY_SECTIONS}/>
                    </>
                ) : (
                    <TermsSections sections={TERMS_SECTIONS}/>
                )}
            </div>
            <div className="flex gap-3 border-t border-[#e4e6eb] px-7 pt-4 pb-6 max-sm:px-5">
                <button
                    type="button"
                    onClick={onClose}
                    className="h-[52px] flex-1 rounded-xl border border-[#e4e6eb] bg-white text-base leading-[normal] font-medium text-[#111318]">
                    닫기
                </button>
                <button
                    type="button"
                    onClick={onAgree}
                    className="h-[52px] flex-1 rounded-xl bg-indigo-700 text-base leading-[normal] font-bold text-white hover:bg-indigo-800">
                    동의하기
                </button>
            </div>
        </dialog>
    )
}

function TermsSections({sections}: { sections: { title: string, paragraphs: string[] }[] }) {
    return sections.map(({title, paragraphs}) => (
        <section key={title} className="flex flex-col gap-2">
            <h3 className="text-[15px] leading-[normal] font-bold text-[#111318]">{title}</h3>
            {paragraphs.map(paragraph => (
                <p key={paragraph} className="text-sm leading-[1.65] text-[#3a3f48]">{paragraph}</p>
            ))}
        </section>
    ))
}
