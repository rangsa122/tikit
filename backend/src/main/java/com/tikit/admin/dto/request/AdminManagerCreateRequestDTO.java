package com.tikit.admin.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;

@Getter
@NoArgsConstructor
public class AdminManagerCreateRequestDTO {

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 254, message = "이메일은 254자 이내로 입력해야 합니다.")
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    @Size(min = 10, max = 128, message = "비밀번호는 10자 이상 128자 이하로 입력해야 합니다.")
    private String password;

    @NotBlank(message = "이름은 필수 입력 항목입니다.")
    @Size(max = 50, message = "이름은 50자 이내로 입력해야 합니다.")
    private String name;

    public AdminManagerCreateRequestDTO(String email, String password, String name) {
        setEmail(email);
        this.password = password;
        this.name = name;
    }

    // JSON 바인딩 단계에서 정규화하여 @Valid도 저장 형식과 같은 값으로 검사합니다.
    @JsonSetter("email")
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
