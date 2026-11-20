package com.tikit.performance.dto.response;

/**
 * 이미지 업로드 결과. 반환된 주소를 공연 등록/수정 요청의 posterUrl 또는 bannerUrl에 넣는다.
 */
public record ImageUploadResponseDTO(String imageUrl) {
}
