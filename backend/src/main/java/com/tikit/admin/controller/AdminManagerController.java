package com.tikit.admin.controller;

import com.tikit.admin.dto.request.AdminManagerCreateRequestDTO;
import com.tikit.admin.dto.response.AdminManagerCreateResponseDTO;
import com.tikit.admin.service.AdminManagerService;
import com.tikit.common.dto.ResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/managers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminManagerController {

    private final AdminManagerService adminManagerService;

    @PostMapping
    public ResponseEntity<ResponseDTO<AdminManagerCreateResponseDTO>> createManager(
            @AuthenticationPrincipal String actorId,
            @Valid @RequestBody AdminManagerCreateRequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ResponseDTO.success("MANAGER 계정 생성 성공",
                        adminManagerService.createManager(Long.parseLong(actorId), request)));
    }
}
