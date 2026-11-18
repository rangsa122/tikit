package com.tikit.common.job;

// 작업 종류별 처리기 (빈으로 등록하면 스케줄러가 type()으로 찾아 실행)
public interface BackgroundJobHandler {

    // 이 처리기가 맡는 작업 종류
    BackgroundJobType type();

    // 점유한 작업 하나 처리: 대상 확인 → (트랜잭션 밖) 외부 조회 → 결과 반영과 작업 마무리
    void handle(LeasedJob job);
}