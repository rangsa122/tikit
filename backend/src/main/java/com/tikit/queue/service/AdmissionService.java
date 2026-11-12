package com.tikit.queue.service;

import com.tikit.common.ErrorCode;
import com.tikit.queue.config.QueueProperties;
import com.tikit.queue.exception.QueueException;
import com.tikit.queue.redis.QueueRedisRepository;
import com.tikit.queue.redis.QueueRedisRepository.Admission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 예매 생성에서 쓰는 입장 권한 검사
 *
 * tikit.queue.admission-required=false면 검사하지 않음 (대기열 없이 예매를 시험할 때)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final QueueRedisRepository queueRedisRepository;
    private final QueueProperties properties;

    public boolean isRequired() {
        return properties.admissionRequired();
    }

    // 입장 권한이 이 회원과 회차의 것이고 아직 유효한지 확인. 아니면 ADMISSION_INVALID
    public void validate(UUID admissionId, Long memberId, Long sessionId) {
        if (!isRequired()) {
            return;
        }
        Admission admission;
        try {
            admission = queueRedisRepository.findAdmission(admissionId)
                    .orElseThrow(() -> new QueueException(ErrorCode.ADMISSION_INVALID));
        } catch (DataAccessException e) {
            log.error("입장 권한 조회 실패: admissionId={}", admissionId, e);
            throw new QueueException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        if (!admission.memberId().equals(memberId) || !admission.sessionId().equals(sessionId)) {
            throw new QueueException(ErrorCode.ADMISSION_INVALID);
        }
    }

    // 예매를 만든 뒤 입장 권한을 다 쓴 것으로 표시하고 정원을 돌려줌
    // 실패해도 예매는 이미 만들어졌으므로 막지 않음 (권한은 TTL로 만료됨)
    public void consume(UUID admissionId) {
        if (!isRequired()) {
            return;
        }
        try {
            queueRedisRepository.consumeAdmission(admissionId);
        } catch (DataAccessException e) {
            log.warn("입장 권한 소비 실패, TTL 만료로 정리됨: admissionId={}", admissionId, e);
        }
    }
}
