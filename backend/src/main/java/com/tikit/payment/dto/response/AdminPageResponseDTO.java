package com.tikit.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

// 관리자 목록 공통 페이지 응답 (결제 내역, 취소·환불 내역)
@Getter
@AllArgsConstructor
public class AdminPageResponseDTO<T> {
    private List<T> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}