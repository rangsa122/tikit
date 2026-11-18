package com.tikit.member.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.exception.MemberStatusChangeException;
import com.tikit.auth.redis.RefreshTokenRepository;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.booking.entity.BookingStatus;
import com.tikit.booking.repository.BookingRepository;
import com.tikit.common.ErrorCode;
import com.tikit.member.dto.MemberProfileResponseDTO;
import com.tikit.member.dto.MemberProfileUpdateRequestDTO;
import java.time.Instant;
import com.tikit.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional(readOnly = true)
    public MemberProfileResponseDTO getMyProfile(Long memberId) {

        Member member = memberRepository.findByIdForUpdate(memberId)
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

    // 유효 예매 확인 후 회원 탈퇴
    @Transactional
    public void withdrawMyAccount(Long memberId) {

        // 회원 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new IllegalArgumentException("회원을 찾을 수 없습니다.")
                );

        // 관람 전 확정 예매 존재 여부 확인
        boolean hasActiveBooking =
                bookingRepository.existsByMemberIdAndStatusAndStartsAtSnapshotAfter(
                        memberId,
                        BookingStatus.CONFIRMED,
                        Instant.now()
                );

        // 유효 예매가 있으면 탈퇴 제한
        if (hasActiveBooking) {
            throw new MemberStatusChangeException(ErrorCode.WITHDRAWAL_BLOCKED);
        }

        // 회원 상태를 WITHDRAWN으로 변경
        member.withdraw();

        // 탈퇴 DB 커밋 완료 후 Refresh Token 삭제
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            refreshTokenRepository.delete(memberId.toString());
                        } catch (RuntimeException e) {
                            // Redis 삭제 실패 시 기록 (회원 탈퇴는 이미 완료됨)
                            log.warn("탈퇴 회원 Refresh Token 삭제 실패: memberId={}", memberId, e);
                        }
                    }
                }
        );
    }
}
