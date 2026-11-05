package com.tikit.queue.entity;

public enum QueueEntryStatus {
    // 순번을 기다리는 중
    WAITING,
    // 입장 권한을 받아 좌석 선택 가능
    ADMITTED,
    // 입장 권한으로 예매(좌석 선점)를 만듦
    CONSUMED,
    // 본인이 대기를 취소하거나 입장을 종료함
    LEFT,
    // 입장 권한 시간이 지났거나 대기 중 조회가 끊김
    EXPIRED
}
