package com.tikit.performance.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.dto.response.PosterUploadResponseDTO;
import com.tikit.performance.service.PerformanceAccessChecker;
import com.tikit.performance.service.PosterStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/manager/posters")
@RequiredArgsConstructor
public class PosterUploadController {

    private final PosterStorageService posterStorageService;
    private final PerformanceAccessChecker accessChecker;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseDTO<PosterUploadResponseDTO>> uploadPoster(
            @AuthenticationPrincipal String memberId,
            @RequestPart("file") MultipartFile file) {

        accessChecker.requireManager(Long.valueOf(memberId));

        String posterUrl = posterStorageService.store(file);

        return ResponseEntity.ok(ResponseDTO.success(
                "포스터가 업로드되었습니다.",
                new PosterUploadResponseDTO(posterUrl)));
    }
}