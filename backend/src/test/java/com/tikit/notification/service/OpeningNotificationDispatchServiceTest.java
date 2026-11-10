package com.tikit.notification.service;

import com.tikit.notification.repository.SaleOpeningLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class OpeningNotificationDispatchServiceTest {

    private static final Long OPENING_ID = 1L;
    private static final Long PERFORMANCE_ID = 10L;

    private SaleOpeningLookupRepository repository;
    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;
    private OpeningNotificationDispatchService service;

    @BeforeEach
    void setup() {
        repository = mock(SaleOpeningLookupRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        transactionTemplate = mock(TransactionTemplate.class);

        service = new OpeningNotificationDispatchService(
                repository,
                jdbcTemplate,
                transactionTemplate
        );

        // 가짜 트랜잭션에서 전달받은 작업 실행
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    // 테스트용 예매 시작 일정
    private SaleOpeningLookupRepository.SaleOpeningTarget target() {
        return new SaleOpeningLookupRepository.SaleOpeningTarget(
                OPENING_ID,
                PERFORMANCE_ID,
                Instant.parse("2026-11-10T01:00:00Z")
        );
    }

    @Test
    @DisplayName("처리할 일정이 없으면 알림 작업을 실행하지 않는다")
    void dispatch_noTargets() {
        // given
        given(repository.findPendingOpenings())
                .willReturn(List.of());

        // when
        service.dispatchPendingNotifications();

        // then
        then(transactionTemplate).shouldHaveNoInteractions();
        then(jdbcTemplate).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("처리 대상 일정의 잠금에 성공하면 알림을 저장하고 완료 처리한다")
    void dispatch_success() {
        // given
        given(repository.findPendingOpenings())
                .willReturn(List.of(target()));

        given(jdbcTemplate.queryForList(
                anyString(),
                eq(Long.class),
                eq(OPENING_ID)
        )).willReturn(List.of(OPENING_ID));

        // when
        service.dispatchPendingNotifications();

        // then
        then(jdbcTemplate).should().update(
                anyString(),
                eq(OPENING_ID),
                eq(PERFORMANCE_ID)
        );

        then(repository).should().markProcessed(OPENING_ID);
    }

    @Test
    @DisplayName("다른 작업이 처리 중인 일정은 건너뛴다")
    void dispatch_lockedByOtherWorker() {
        // given
        given(repository.findPendingOpenings())
                .willReturn(List.of(target()));

        // 잠금에 실패한 상황
        given(jdbcTemplate.queryForList(
                anyString(),
                eq(Long.class),
                eq(OPENING_ID)
        )).willReturn(List.of());

        // when
        service.dispatchPendingNotifications();

        // then
        then(jdbcTemplate).should(never()).update(
                anyString(),
                any(),
                any()
        );

        then(repository).should(never()).markProcessed(anyLong());
    }

    @Test
    @DisplayName("알림 저장에 실패하면 처리 완료로 기록하지 않는다")
    void dispatch_insertFailure() {
        // given
        given(repository.findPendingOpenings())
                .willReturn(List.of(target()));

        given(jdbcTemplate.queryForList(
                anyString(),
                eq(Long.class),
                eq(OPENING_ID)
        )).willReturn(List.of(OPENING_ID));

        given(jdbcTemplate.update(
                anyString(),
                eq(OPENING_ID),
                eq(PERFORMANCE_ID)
        )).willThrow(new IllegalStateException("알림 저장 실패"));

        // when, then
        assertThatThrownBy(service::dispatchPendingNotifications)
                .isInstanceOf(IllegalStateException.class);

        then(repository).should(never()).markProcessed(anyLong());
    }
}
