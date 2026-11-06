package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.exception.PerformanceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class PerformanceAccessChecker {

    private final MemberRepository memberRepository;

    // 쓰기: MANAGER만
    public Member requireManager(Long memberId) {
        Member member = getActiveMember(memberId);
        if (member.getRole() != Role.MANAGER) {
            throw new PerformanceException(ErrorCode.ACCESS_DENIED);
        }
        return member;
    }

    // 조회: MANAGER, ADMIN
    public Member requireViewer(Long memberId) {
        Member member = getActiveMember(memberId);
        if (member.getRole() != Role.MANAGER && member.getRole() != Role.ADMIN) {
            throw new PerformanceException(ErrorCode.ACCESS_DENIED);
        }
        return member;
    }

    private Member getActiveMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new PerformanceException(ErrorCode.ACCESS_DENIED));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new PerformanceException(ErrorCode.ACCOUNT_RESTRICTED);
        }
        return member;
    }
}