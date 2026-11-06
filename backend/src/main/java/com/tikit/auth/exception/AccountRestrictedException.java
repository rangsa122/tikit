package com.tikit.auth.exception;

// 정지 또는 탈퇴 계정의 인증 요청 거부
public class AccountRestrictedException extends RuntimeException {
    public AccountRestrictedException(String message) {
        super(message);
    }
}
