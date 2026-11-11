package com.tikit.performance.service;

import com.tikit.performance.dto.response.GenreListResponseDTO;
import com.tikit.performance.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenreService {

    private final GenreRepository genreRepository;

    public GenreListResponseDTO getActiveGenres() {
        return GenreListResponseDTO.from(genreRepository.findAllActive());
    }
}