package com.tikit.performance.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.dto.SeatTemplateListResponseDTO;
import com.tikit.performance.service.SalesConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
public class SalesConfigurationController {

    private final SalesConfigurationService service;

    @GetMapping("/seat-templates")
    public ResponseEntity<ResponseDTO<SeatTemplateListResponseDTO>> listSeatTemplates(@AuthenticationPrincipal String memberId){
        return  ResponseEntity.ok(ResponseDTO.success(" 좌석 템플릿 목록 조회에 성공했습니다",
                service.listSeatTemplate(Long.valueOf(memberId))));
    }
}
