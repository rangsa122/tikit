package com.tikit.admin.bootstrap;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "bootstrap.admin")
public class AdminBootstrapProperties {
    @NotBlank
    @Email
    @Size(max = 254)
    private String email = "test1234@naver.com";

    @NotBlank
    @Size(max = 50)
    private String name = "개발 관리자";

    private String password = "123456789a";

    // 검증 오류에도 비밀번호 원문 대신 이 boolean 속성만 남도록 검증합니다.
    @AssertTrue(message = "초기 관리자 비밀번호는 필수이며 10~128자, UTF-8 기준 72바이트 이하여야 합니다.")
    public boolean isPasswordValid() {
        return password != null && !password.isBlank() && password.length() >= 10
                && password.length() <= 128 && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
