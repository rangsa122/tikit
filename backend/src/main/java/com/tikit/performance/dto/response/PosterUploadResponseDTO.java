package com.tikit.performance.dto.response;

/**
 * 포스터 업로드 결과. posterUrl을 공연 등록/수정 요청에 그대로 넣어 사용
 */
public record PosterUploadResponseDTO(String posterUrl) {
}