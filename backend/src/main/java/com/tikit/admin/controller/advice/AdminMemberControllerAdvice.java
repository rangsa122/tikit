package com.tikit.admin.controller.advice;

import com.tikit.admin.controller.AdminMemberController;
import com.tikit.admin.exception.AdminMemberNotFoundException;
import com.tikit.auth.exception.MemberStatusChangeException;
import com.tikit.common.ErrorCode;
import com.tikit.common.dto.ResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = AdminMemberController.class)
public class AdminMemberControllerAdvice {

    @ExceptionHandler(MemberStatusChangeException.class)
    public ResponseEntity<ResponseDTO<Void>> handleStatusChange(MemberStatusChangeException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ResponseDTO.fail(errorCode));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ResponseDTO<Void>> handleVersionConflict(ObjectOptimisticLockingFailureException exception) {
        ErrorCode errorCode = ErrorCode.VERSION_CONFLICT;
        return ResponseEntity.status(errorCode.getStatus()).body(ResponseDTO.fail(errorCode));
    }

    @ExceptionHandler(AdminMemberNotFoundException.class)
    public ResponseEntity<ResponseDTO<Void>> handleNotFound(AdminMemberNotFoundException exception) {
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
        return ResponseEntity.status(errorCode.getStatus()).body(ResponseDTO.fail(errorCode));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ResponseDTO<Void>> handleInvalidParameter(Exception exception) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(errorCode.getStatus()).body(ResponseDTO.fail(errorCode));
    }
}
