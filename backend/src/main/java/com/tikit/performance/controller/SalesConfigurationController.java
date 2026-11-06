package com.tikit.performance.controller;

import com.tikit.common.dto.ResponseDTO;
import com.tikit.performance.dto.response.SeatTemplateListResponseDTO;
import com.tikit.performance.dto.request.SalesConfigurationSaveRequestDTO;
import com.tikit.performance.dto.response.SalesConfigurationResponseDTO;
import com.tikit.performance.service.SalesConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/performances/{performanceId}/sales-configuration")
    public ResponseEntity<ResponseDTO<SalesConfigurationResponseDTO>> getSalesConfiguration(
            @AuthenticationPrincipal String memberId, @PathVariable Long performanceId){
        return ResponseEntity.ok(ResponseDTO.success("판매설정 조회에 성공했습니다",
                service.getSalesConfiguration(Long.valueOf(memberId), performanceId)));
    }

    @PutMapping("/performances/{performanceId}/sales-configuration")
    public ResponseEntity<ResponseDTO<SalesConfigurationResponseDTO>> saveSalesConfiguration(
            @AuthenticationPrincipal String memberId, @PathVariable Long performanceId,
            @Valid @RequestBody SalesConfigurationSaveRequestDTO request){
        return ResponseEntity.ok(ResponseDTO.success("판매설정 저장에 성공했습니다",
                service.saveSalesConfiguration(Long.valueOf(memberId), performanceId,request)));
    }
}
