package com.tikit.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 대기열 설정 (tikit.queue.*)
 *
 * @param capacity          회차마다 동시에 좌석 화면에 들어갈 수 있는 인원
 * @param admissionTtl      입장 권한 유지 시간 (이 안에 좌석을 선점해야 함)
 * @param waitingTimeout    대기 중 조회가 끊긴 뒤 자리를 비워 두는 시간 (창을 닫은 사람 정리)
 *                          브라우저가 숨은 탭의 타이머를 1분에 한 번으로 늦추므로 1분보다 넉넉하게 둠
 * @param pollInterval      대기 중 다음 조회까지 기다릴 시간
 * @param maxAdmitPerCall   요청 한 번에 새로 입장시키는 최대 인원 (한꺼번에 몰리지 않게)
 * @param entryTtl          대기 항목을 Redis에 남겨 두는 시간
 * @param admissionRequired 예매 생성 때 입장 권한을 검사할지 여부
 */
@ConfigurationProperties(prefix = "tikit.queue")
public record QueueProperties(
        @DefaultValue("100") int capacity,
        @DefaultValue("10m") Duration admissionTtl,
        @DefaultValue("2m") Duration waitingTimeout,
        @DefaultValue("2s") Duration pollInterval,
        @DefaultValue("20") int maxAdmitPerCall,
        @DefaultValue("6h") Duration entryTtl,
        @DefaultValue("true") boolean admissionRequired
) {
}
