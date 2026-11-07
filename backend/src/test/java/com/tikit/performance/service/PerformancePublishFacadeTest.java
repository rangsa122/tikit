package com.tikit.performance.service;

import com.tikit.common.ErrorCode;
import com.tikit.common.idempotency.IdempotencyService;
import com.tikit.common.idempotency.IdempotencyStart;
import com.tikit.performance.exception.PerformanceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PerformancePublishFacadeTest {

    @Mock
    PerformancePublishService publishService;

    @Mock
    IdempotencyService idempotencyService;

    @InjectMocks
    PerformancePublishFacade facade;

    private static final Long MEMBER_ID = 1L;
    private static final Long PERFORMANCE_ID = 10L;
    private static final Long REQUEST_ID = 999L;
    private static final String KEY = "test-idempotency-key";
    private static final String OPERATION_ID = "PERFORMANCE_PUBLISH";

    @Test
    @DisplayName("첫 요청이면 게시를 실행한다")
    void publish_firstRequest() {
        when(idempotencyService.start(eq(MEMBER_ID), eq(OPERATION_ID), eq(KEY), anyString()))
                .thenReturn(IdempotencyStart.started(REQUEST_ID));

        facade.publish(MEMBER_ID, PERFORMANCE_ID, 0L, KEY);

        verify(publishService).publish(MEMBER_ID, PERFORMANCE_ID, 0L, REQUEST_ID);
        verify(publishService, never()).getPublication(anyLong(), anyLong());
        verify(idempotencyService, never()).fail(anyLong());
    }

    @Test
    @DisplayName("같은 키로 다시 요청하면 게시하지 않고 저장된 결과를 돌려준다")
    void publish_replay() {
        when(idempotencyService.start(eq(MEMBER_ID), eq(OPERATION_ID), eq(KEY), anyString()))
                .thenReturn(IdempotencyStart.replay(REQUEST_ID, "PERFORMANCE", PERFORMANCE_ID));

        facade.publish(MEMBER_ID, PERFORMANCE_ID, 0L, KEY);

        verify(publishService).getPublication(MEMBER_ID, PERFORMANCE_ID);
        verify(publishService, never()).publish(anyLong(), anyLong(), anyLong(), anyLong());
        verify(idempotencyService, never()).fail(anyLong());
    }

    @Test
    @DisplayName("게시가 실패하면 멱등 기록을 실패로 남기고 예외를 그대로 던진다")
    void publish_failure() {
        when(idempotencyService.start(eq(MEMBER_ID), eq(OPERATION_ID), eq(KEY), anyString()))
                .thenReturn(IdempotencyStart.started(REQUEST_ID));
        when(publishService.publish(MEMBER_ID, PERFORMANCE_ID, 0L, REQUEST_ID))
                .thenThrow(new PerformanceException(ErrorCode.VERSION_CONFLICT));

        assertThatThrownBy(() -> facade.publish(MEMBER_ID, PERFORMANCE_ID, 0L, KEY))
                .isInstanceOf(PerformanceException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VERSION_CONFLICT);

        verify(idempotencyService).fail(REQUEST_ID);
    }
}