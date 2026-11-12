import {useNavigate, useParams, useSearchParams} from 'react-router'
import Button from '../../components/common/Button'
import {AlertIcon, CenterColumn, Notice, StatusCard} from '../../components/payment/PaymentUi'

// 회원이 결제창에서 직접 취소했을 때 토스가 보내는 코드
const USER_CANCELED_CODE = 'PAY_PROCESS_CANCELED'

/**
 * 토스 결제 실패 URL 페이지 (/bookings/:bookingId/payment/fail)
 *
 * 승인 API를 부르기 전(결제창 인증 단계)의 실패라 결제는 되지 않음 → 서버 호출 없음
 * 결제 시도는 READY 그대로라, 기한 안이면 결제 화면에서 같은 주문으로 다시 결제할 수 있음
 * 주소창 값은 누구나 바꿀 수 있어서 토스 message는 보여 주지 않고 code로만 문구를 고름
 */
export default function PaymentFailPage() {
    const {bookingId} = useParams()
    const navigate = useNavigate()
    const [searchParams] = useSearchParams()
    const code = searchParams.get('code')
    const isUserCanceled = code === USER_CANCELED_CODE
    const paymentPath = `/bookings/${bookingId}/payment`

    return (
        <main className="mx-auto max-w-[1320px] px-5 py-8">
            <CenterColumn>
                <StatusCard
                    icon={<AlertIcon />}
                    title={isUserCanceled ? '결제를 취소했어요' : '결제가 완료되지 않았어요'}
                >
                    {isUserCanceled
                        ? '결제창에서 결제를 취소해 결제가 진행되지 않았어요.'
                        : '결제 인증 중 문제가 생겨 결제가 진행되지 않았어요.'}
                    <br />
                    카드 결제 금액은 청구되지 않아요.
                </StatusCard>
                <Notice>선택한 좌석은 남은 시간 동안 유지돼요. 시간 안에 결제 화면에서 다시 결제할 수 있어요.</Notice>
                {code && !isUserCanceled && (
                    <p className="text-center text-xs text-gray-400">오류 코드 {code}</p>
                )}
                <Button onClick={() => navigate(paymentPath, {replace: true})}>결제 화면으로</Button>
            </CenterColumn>
        </main>
    )
}