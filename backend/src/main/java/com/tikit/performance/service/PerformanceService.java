package com.tikit.performance.service;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.common.ErrorCode;
import com.tikit.performance.dto.PerformanceCreateRequestDTO;
import com.tikit.performance.dto.PerformanceResponseDTO;
import com.tikit.performance.dto.PerformanceUpdateRequestDTO;
import com.tikit.performance.entity.Genre;
import com.tikit.performance.entity.Performance;
import com.tikit.performance.entity.PerformanceSource;
import com.tikit.performance.entity.PublicationStatus;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.repository.GenreRepository;
import com.tikit.performance.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerformanceService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PerformanceRepository performanceRepository;
    private final GenreRepository genreRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public PerformanceResponseDTO create(Long memberId, PerformanceCreateRequestDTO request){
        Member manager = getManager(memberId);
        validateSource(request);
        validateDates(request.getStartDate(), request.getEndDate());
        Genre genre = findActiveGenre(request.getGenreCode());

        Performance performance;
        if(request.getSource() == PerformanceSource.MANUAL){
            performance = Performance.createManual(manager, genre, request.getTitle(),
                    request.getVenueName(), request.getOrganizerName(), request.getDescription(),
                    request.getPosterUrl(), request.getStartDate(), request.getEndDate(),
                    request.getBookingLimitPerMember());
        }else {
            if (performanceRepository.existsByKopisId(request.getKopisId())) {
                throw new PerformanceException(ErrorCode.KOPIS_ALREADY_REGISTERED);
            }
            performance = Performance.createFromKopis(manager, genre, request.getKopisId(),
                    request.getTitle(), request.getVenueName(), request.getOrganizerName(),
                    request.getDescription(), request.getPosterUrl(), request.getStartDate(),
                    request.getEndDate(), request.getBookingLimitPerMember());
        }
        performanceRepository.save(performance);
        return PerformanceResponseDTO.from(performance);
    }

    public Page<PerformanceResponseDTO> list(Long memberId, PublicationStatus status,
                                             int page, int size) {
        getViewer(memberId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return performanceRepository
                .findAllByStatus(status, PageRequest.of(safePage, safeSize))
                .map(PerformanceResponseDTO::from);
    }

    public PerformanceResponseDTO get(Long memberId, Long performanceId) {
        getViewer(memberId);
        return PerformanceResponseDTO.from(findPerformance(performanceId));
    }

    @Transactional
    public PerformanceResponseDTO update(Long memberId, Long performanceId,
                                         PerformanceUpdateRequestDTO request) {
        getManager(memberId);
        Performance performance = findPerformance(performanceId);

        if (performance.getPublicationStatus() != PublicationStatus.DRAFT) {
            throw new PerformanceException(ErrorCode.PERFORMANCE_EDIT_LOCKED);
        }
        if (!performance.getVersion().equals(request.getExpectedVersion())) {
            throw new PerformanceException(ErrorCode.VERSION_CONFLICT);
        }

        LocalDate newStart = request.getStartDate() != null
                ? request.getStartDate() : performance.getStartDate();
        LocalDate newEnd = request.getEndDate() != null
                ? request.getEndDate() : performance.getEndDate();
        validateDates(newStart, newEnd);

        Genre genre = request.getGenreCode() == null
                ? null : findActiveGenre(request.getGenreCode());

        performance.update(genre, request.getTitle(), request.getVenueName(),
                request.getOrganizerName(), request.getDescription(), request.getPosterUrl(),
                request.getStartDate(), request.getEndDate(), request.getBookingLimitPerMember());

        performanceRepository.flush();   // 새 version, updatedAt이 응답에 반영되도록
        return PerformanceResponseDTO.from(performance);
    }




    private Member getManager(Long memberId){
        Member member = memberRepository.findById(memberId)
                .orElseThrow(()->new PerformanceException(ErrorCode.ACCESS_DENIED));
        if(member.getStatus() != MemberStatus.ACTIVE){
            throw new PerformanceException(ErrorCode.ACCOUNT_RESTRICTED);
        }
        if(member.getRole() != Role.MANAGER){
            throw new PerformanceException(ErrorCode.ACCESS_DENIED);
        }
        return member;
    }

    private Member getViewer(Long memberId){
        Member member = memberRepository.findById(memberId)
                .orElseThrow(()->new PerformanceException(ErrorCode.ACCESS_DENIED));
        if(member.getStatus() != MemberStatus.ACTIVE){
            throw new PerformanceException(ErrorCode.ACCOUNT_RESTRICTED);
        }
        if(member.getRole() != Role.MANAGER && member.getRole() != Role.ADMIN){
            throw new PerformanceException(ErrorCode.ACCESS_DENIED);
        }
        return member;
    }

    private Performance findPerformance(Long id){
        return performanceRepository.findDetail(id)
                .orElseThrow(()->new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Genre findActiveGenre(String code) {
        return genreRepository.findActive(code)
                .orElseThrow(() -> new PerformanceException(ErrorCode.VALIDATION_ERROR));
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateSource(PerformanceCreateRequestDTO request){
        boolean hasKopisId = StringUtils.hasText(request.getKopisId());
        if(request.getSource() == PerformanceSource.MANUAL && hasKopisId){
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }
        if(request.getSource() == PerformanceSource.KOPIS && !hasKopisId){
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }
    }



}
