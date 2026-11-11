package com.tikit.performance.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.dto.response.GenreListResponseDTO;
import com.tikit.performance.service.GenreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/genres")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService service;

    @GetMapping
    public ResponseEntity<ResponseDTO<GenreListResponseDTO>> getGenres() {
        return ResponseEntity.ok(ResponseDTO.success("장르 목록 조회에 성공했습니다",
                service.getActiveGenres()));
    }
}