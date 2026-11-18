package com.tikit.member.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.exception.MemberStatusChangeException;
import com.tikit.auth.redis.RefreshTokenRepository;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.entity.Booking;
import com.tikit.booking.service.BookingExpiryTxService;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.common.ErrorCode;
import com.tikit.member.dto.MemberProfileResponseDTO;
import com.tikit.member.dto.MemberProfileUpdateRequestDTO;
import com.tikit.member.dto.MemberWithdrawalEligibilityResponseDTO;
import com.tikit.payment.entity.CancellationStatus;
import com.tikit.payment.entity.PaymentAttemptStatus;
import com.tikit.payment.repository.CancellationRepository;
import com.tikit.payment.repository.PaymentAttemptRepository;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

// 회원 엔티티 조회 및 응답 DTO 변환
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private final MemberRepository memberRepository;
    private final BookingRepository bookingRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BookingExpiryTxService bookingExpiryTxService;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CancellationRepository cancellationRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public MemberProfileResponseDTO getMyProfile(Long memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new IllegalArgumentException("회원을 찾을 수 없습니다.")
                );

        return MemberProfileResponseDTO.from(member);
    }

    @Transactional
    public MemberProfileResponseDTO updateMyProfile(
            Long memberId,
            MemberProfileUpdateRequestDTO request
    ) {

        // 회원 ID로 DB에서 회원 조회
        Member member = memberRepository.findById(memberId)

                // 해당 회원이 존재하지 않으면 예외 발생
                .orElseThrow(() ->
                        new IllegalArgumentException("회원을 찾을 수 없습니다.")
                );

        String newName = request.name();
        member.changeName(newName);
        return MemberProfileResponseDTO.from(member);
    }

    // 회원 탈퇴 가능 여부 조회
    @Transactional
    public MemberWithdrawalEligibilityResponseDTO getWithdrawalEligibility(Long memberId) {

        // 예매 생성과 동일하게 회원 행 잠금
        memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new IllegalArgumentException("회원을 찾을 수 없습니다.")
                );

        Instant now = Instant.now();

        // 만료된 선점 정리 및 유효한 선점 확인
        boolean hasActiveHold = false;

        List<Booking> pendingBookings = bookingRepository.findByMemberIdAndStatus(memberId, BookingStatus.PENDING_PAYMENT);

        for (Booking booking : pendingBookings) {
            if (booking.isExpired(now) && bookingExpiryTxService.expire(booking.getId(), now)) {
                continue;
            }

            hasActiveHold = true;
        }

        // 관람 전 확정 예매 확인
        boolean hasActiveBooking = bookingRepository.existsByMemberIdAndStatusAndStartsAtSnapshotAfter(memberId, BookingStatus.CONFIRMED, now);

        // 처리 중이거나 확인 중인 결제 확인
        boolean hasPendingPayment =
                paymentAttemptRepository.existsByMemberIdAndStatusIn(
                        memberId,
                        List.of(
                                PaymentAttemptStatus.PROCESSING,
                                PaymentAttemptStatus.RECONCILING
                        )
                );

        // 진행 중인 환불 확인
        boolean hasPendingCancellation =
                cancellationRepository.existsByMemberIdAndStatusIn(
                        memberId,
                        List.of(
                                CancellationStatus.REQUESTED,
                                CancellationStatus.PROCESSING,
                                CancellationStatus.RECONCILING
                        )
                );

        // 제한 조건이 하나도 없으면 탈퇴 가능
        boolean eligible = !(hasActiveHold || hasActiveBooking || hasPendingPayment || hasPendingCancellation);

        return new MemberWithdrawalEligibilityResponseDTO(eligible);
    }

    // 유효 예매 확인 후 회원 탈퇴
    @Transactional(noRollbackFor = MemberStatusChangeException.class)
    public void withdrawMyAccount(Long memberId, String password) {

        // 회원 조회 및 잠금
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new IllegalArgumentException("회원을 찾을 수 없습니다.")
                );

        // 비밀번호 확인
        if (password == null || !passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new MemberStatusChangeException(ErrorCode.ACCESS_DENIED);
        }

        Instant now = Instant.now();

        List<Booking> pendingBookings = bookingRepository.findByMemberIdAndStatus(memberId, BookingStatus.PENDING_PAYMENT);

        // 만료된 선점 정리 및 유효한 선점 확인
        boolean hasActiveHold = false;

        for (Booking booking : pendingBookings) {
            if (booking.isExpired(now) && bookingExpiryTxService.expire(booking.getId(), now)) {
                continue;
            }

            hasActiveHold = true;
        }

        // 모든 예매 확인 후 탈퇴 제한
        if (hasActiveHold) {
            throw new MemberStatusChangeException(ErrorCode.WITHDRAWAL_BLOCKED);
        }

        // 관람 전 확정 예매 확인
        boolean hasActiveBooking = bookingRepository.existsByMemberIdAndStatusAndStartsAtSnapshotAfter(memberId, BookingStatus.CONFIRMED, now);

        if (hasActiveBooking) {
            throw new MemberStatusChangeException(ErrorCode.WITHDRAWAL_BLOCKED);
        }

        // 처리 중이거나 확인 중인 결제 확인
        boolean hasPendingPayment =
                paymentAttemptRepository.existsByMemberIdAndStatusIn(
                        memberId,
                        List.of(
                                PaymentAttemptStatus.PROCESSING,
                                PaymentAttemptStatus.RECONCILING
                        )
                );

        if (hasPendingPayment) {
            throw new MemberStatusChangeException(ErrorCode.WITHDRAWAL_BLOCKED);
        }

        // 진행 중인 환불 확인
        boolean hasPendingCancellation =
                cancellationRepository.existsByMemberIdAndStatusIn(
                        memberId,
                        List.of(
                                CancellationStatus.REQUESTED,
                                CancellationStatus.PROCESSING,
                                CancellationStatus.RECONCILING
                        )
                );

        if (hasPendingCancellation) {
            throw new MemberStatusChangeException(ErrorCode.WITHDRAWAL_BLOCKED);
        }

        // 회원 상태 변경
        member.withdraw();

        // DB 커밋 완료 후 Refresh Token 삭제
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            refreshTokenRepository.delete(memberId.toString());
                        } catch (RuntimeException e) {
                            log.warn("탈퇴 회원 Refresh Token 삭제 실패: memberId={}", memberId, e);
                        }
                    }
                }
        );
    }
}
