
package com.tikit.performance.controller;

import com.tikit.common.dto.PageResponseDTO;
import com.tikit.performance.dto.response.FavoritePerformanceResponseDTO;
import com.tikit.performance.exception.PerformanceException;
import com.tikit.performance.service.FavoritePerformanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
        assertThat(response.getBody().getCode())
                .isEqualTo("SUCCESS");

        var data = response.getBody().getData();

        assertThat(data).isNotNull();
        assertThat(data.performanceId())
                .isEqualTo("10");
        assertThat(data.favorited())
                .isTrue();

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
    void getFavorites_success() {
        Page<FavoritePerformanceResponseDTO> favorites =
                new PageImpl<>(
                        List.of(),
                        PageRequest.of(0, 20),
                        0
                );

        when(favoritePerformanceService.getFavorites(1L, 0, 20))
                .thenReturn(favorites);

        var response = controller.getFavorites("1", 0, 20);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode())
                .isEqualTo("SUCCESS");

        PageResponseDTO<FavoritePerformanceResponseDTO> data =
                response.getBody().getData();

        assertThat(data).isNotNull();
        assertThat(data.getItems()).isEmpty();
        assertThat(data.getPage()).isZero();
        assertThat(data.getSize()).isEqualTo(20);
        assertThat(data.getTotalElements()).isZero();
        assertThat(data.getTotalPages()).isZero();

        verify(favoritePerformanceService)
                .getFavorites(1L, 0, 20);
    }

    @Test
    void getFavorites_customPageAndSize() {
        Page<FavoritePerformanceResponseDTO> favorites =
                new PageImpl<>(
                        List.of(),
                        PageRequest.of(1, 10),
                        15
                );

        when(favoritePerformanceService.getFavorites(1L, 1, 10))
                .thenReturn(favorites);

        var response = controller.getFavorites("1", 1, 10);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).isNotNull();

        var data = response.getBody().getData();

        assertThat(data).isNotNull();
        assertThat(data.getPage()).isEqualTo(1);
        assertThat(data.getSize()).isEqualTo(10);
        assertThat(data.getTotalElements()).isEqualTo(15);
        assertThat(data.getTotalPages()).isEqualTo(2);

        verify(favoritePerformanceService)
                .getFavorites(1L, 1, 10);
    }

    @Test
    void addFavorite_invalidMemberId() {
        assertThatThrownBy(
                () -> controller.addFavorite("invalid", 10L)
        ).isInstanceOf(PerformanceException.class);

        verifyNoInteractions(favoritePerformanceService);
    }

    @Test
    void removeFavorite_missingMemberId() {
        assertThatThrownBy(
                () -> controller.removeFavorite(null, 10L)
        ).isInstanceOf(PerformanceException.class);

        verifyNoInteractions(favoritePerformanceService);
    }

    @Test
    void getFavorites_invalidMemberId() {
        assertThatThrownBy(
                () -> controller.getFavorites("invalid", 0, 20)
        ).isInstanceOf(PerformanceException.class);

        verifyNoInteractions(favoritePerformanceService);
    }
}
