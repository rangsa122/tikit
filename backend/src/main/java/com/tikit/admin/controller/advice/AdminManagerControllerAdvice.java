package com.tikit.admin.controller.advice;

import com.tikit.admin.controller.AdminManagerController;
import com.tikit.auth.exception.AccountRestrictedException;
import com.tikit.auth.exception.DuplicateEmailException;
import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminManagerController.class)
@Slf4j
public class AdminManagerControllerAdvice {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ResponseDTO<Void>> handleDuplicateEmail(DuplicateEmailException exception) {
        return toResponse(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @ExceptionHandler(AccountRestrictedException.class)
    public ResponseEntity<ResponseDTO<Void>> handleRestrictedAccount(AccountRestrictedException exception) {
        return toResponse(ErrorCode.ACCOUNT_RESTRICTED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseDTO<Void>> handleAccessDenied(AccessDeniedException exception) {
        return toResponse(ErrorCode.ACCESS_DENIED);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ResponseDTO<Void>> handleDatabaseFailure(RuntimeException exception) {
        // SQL이나 바인딩 값이 포함될 수 있는 예외 메시지는 응답과 로그에 노출하지 않습니다.
        log.error("MANAGER 계정 생성 DB 처리 실패: {}", exception.getClass().getSimpleName());
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ResponseDTO<Void>> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus()).body(ResponseDTO.fail(errorCode));
    }
}
