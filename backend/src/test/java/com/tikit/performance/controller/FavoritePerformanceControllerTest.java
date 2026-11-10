
package com.tikit.performance.controller;

import com.tikit.common.ErrorCode;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.service.FavoritePerformanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FavoritePerformanceControllerTest {

    private FavoritePerformanceService favoritePerformanceService;
    private FavoritePerformanceController controller;

    @BeforeEach
    void setUp() {
        favoritePerformanceService =
                mock(FavoritePerformanceService.class);

        controller = new FavoritePerformanceController(
                favoritePerformanceService
        );
    }

    @Test
    void addFavorite_success() {
        var response = controller.addFavorite("1", 10L);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).isNotNull();

        verify(favoritePerformanceService)
                .addFavorite(1L, 10L);
    }

    @Test
    void removeFavorite_success() {
        var response = controller.removeFavorite("1", 10L);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(response.getBody()).isNull();

        verify(favoritePerformanceService)
                .removeFavorite(1L, 10L);
    }

    @Test
    void addFavorite_invalidMemberId() {
        assertThatThrownBy(
                () -> controller.addFavorite("invalid", 10L)
        )
                .isInstanceOf(PerformanceException.class)
                .satisfies(exception -> {
                    PerformanceException performanceException =
                            (PerformanceException) exception;

                    assertThat(performanceException.getErrorCode())
                            .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
                });

        verifyNoInteractions(favoritePerformanceService);
    }

    @Test
    void removeFavorite_missingMemberId() {
        assertThatThrownBy(
                () -> controller.removeFavorite(null, 10L)
        )
                .isInstanceOf(PerformanceException.class)
                .satisfies(exception -> {
                    PerformanceException performanceException =
                            (PerformanceException) exception;

                    assertThat(performanceException.getErrorCode())
                            .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
                });

        verifyNoInteractions(favoritePerformanceService);
    }

    @Test
    void addFavorite_blankMemberId() {
        assertThatThrownBy(
                () -> controller.addFavorite("   ", 10L)
        )
                .isInstanceOf(PerformanceException.class)
                .satisfies(exception -> {
                    PerformanceException performanceException =
                            (PerformanceException) exception;

                    assertThat(performanceException.getErrorCode())
                            .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
                });

        verifyNoInteractions(favoritePerformanceService);
    }

    @Test
    void removeFavorite_invalidMemberId() {
        assertThatThrownBy(
                () -> controller.removeFavorite("abc", 10L)
        )
                .isInstanceOf(PerformanceException.class)
                .satisfies(exception -> {
                    PerformanceException performanceException =
                            (PerformanceException) exception;

                    assertThat(performanceException.getErrorCode())
                            .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
                });

        verifyNoInteractions(favoritePerformanceService);
    }

    @Test
    void addFavorite_serviceNotFound() {
        doThrow(new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND))
                .when(favoritePerformanceService)
                .addFavorite(1L, 999999L);

        assertThatThrownBy(
                () -> controller.addFavorite("1", 999999L)
        )
                .isInstanceOf(PerformanceException.class)
                .satisfies(exception -> {
                    PerformanceException performanceException =
                            (PerformanceException) exception;

                    assertThat(performanceException.getErrorCode())
                            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                });

        verify(favoritePerformanceService)
                .addFavorite(1L, 999999L);
    }

    @Test
    void removeFavorite_serviceNotFound() {
        doThrow(new PerformanceException(ErrorCode.RESOURCE_NOT_FOUND))
                .when(favoritePerformanceService)
                .removeFavorite(1L, 999999L);

        assertThatThrownBy(
                () -> controller.removeFavorite("1", 999999L)
        )
                .isInstanceOf(PerformanceException.class)
                .satisfies(exception -> {
                    PerformanceException performanceException =
                            (PerformanceException) exception;

                    assertThat(performanceException.getErrorCode())
                            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                });

        verify(favoritePerformanceService)
                .removeFavorite(1L, 999999L);
    }
}
