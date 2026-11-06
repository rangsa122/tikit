
package com.tikit.performance.controller;

import com.tikit.performance.service.FavoritePerformanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        // 삭제 성공 시 204 No Content 반환
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // 204 응답에는 본문이 없어야 함
        assertThat(response.getBody()).isNull();

        verify(favoritePerformanceService)
                .removeFavorite(1L, 10L);
    }

    @Test
    void getFavorites_success() {
        when(favoritePerformanceService.getFavorites(1L))
                .thenReturn(List.of());

        var response = controller.getFavorites("1");

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).isNotNull();

        verify(favoritePerformanceService)
                .getFavorites(1L);
    }

    @Test
    void addFavorite_invalidMemberId() {
        assertThatThrownBy(
                () -> controller.addFavorite("invalid", 10L)
        ).isInstanceOf(
                com.tikit.performance.exception.PerformanceException.class
        );

        verifyNoInteractions(favoritePerformanceService);
    }

    @Test
    void removeFavorite_missingMemberId() {
        assertThatThrownBy(
                () -> controller.removeFavorite(null, 10L)
        ).isInstanceOf(
                com.tikit.performance.exception.PerformanceException.class
        );

        verifyNoInteractions(favoritePerformanceService);
    }
}
