package com.tikit.member.controller.advice;

import com.tikit.auth.exception.MemberStatusChangeException;
import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import com.tikit.member.controller.MemberProfileController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 회원정보 API 예외 처리
@RestControllerAdvice(assignableTypes = MemberProfileController.class)
public class MemberProfileControllerAdvice {

    // 회원 탈퇴 제한 및 상태 변경 오류 응답
    @ExceptionHandler(MemberStatusChangeException.class)
    public ResponseEntity<ResponseDTO<Void>> handleStatusChange(
            MemberStatusChangeException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity.status(errorCode.getStatus())
                .body(ResponseDTO.fail(errorCode));
    }
}
