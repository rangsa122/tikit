package com.tikit.auth.exception;

// 이미 가입된 이메일로 회원가입 요청 시 거부
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
