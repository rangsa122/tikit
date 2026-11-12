package com.tikit.performance.dto.response;

import com.tikit.performance.entity.Genre;

import java.util.List;

public record GenreListResponseDTO(List<Item> items) {

    // kopis_code와 sort_order는 내부용이라 내리지 않는다
    public record Item(String code, String name) {
    }

    public static GenreListResponseDTO from(List<Genre> genres) {
        return new GenreListResponseDTO(
                genres.stream()
                        .map(genre -> new Item(genre.getCode(), genre.getName()))
                        .toList()
        );
    }
}