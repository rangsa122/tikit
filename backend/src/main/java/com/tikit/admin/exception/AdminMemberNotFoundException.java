package com.tikit.admin.exception;

public class AdminMemberNotFoundException extends RuntimeException {

    public AdminMemberNotFoundException() {
        super("회원을 찾을 수 없습니다.");
    }
}
