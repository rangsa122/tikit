package com.tikit.performance.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.dto.response.ImageUploadResponseDTO;
import com.tikit.performance.service.PerformanceAccessChecker;
import com.tikit.performance.service.ImageStorageService;
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
@RequestMapping("/api/v1/manager/images")
@RequiredArgsConstructor
public class ImageUploadController {

    private final ImageStorageService imageStorageService;
    private final PerformanceAccessChecker accessChecker;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseDTO<ImageUploadResponseDTO>> uploadImage(
            @AuthenticationPrincipal String memberId,
            @RequestPart("file") MultipartFile file) {

        accessChecker.requireManager(Long.valueOf(memberId));

        String imageUrl = imageStorageService.store(file);

        return ResponseEntity.ok(ResponseDTO.success(
                "이미지가 업로드되었습니다.",
                new ImageUploadResponseDTO(imageUrl)));
    }
}
