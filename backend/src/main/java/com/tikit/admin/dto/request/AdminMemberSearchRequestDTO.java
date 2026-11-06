package com.tikit.admin.dto.request;

import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Getter
@Setter
@NoArgsConstructor
public class AdminMemberSearchRequestDTO {

    // 이름 또는 이메일 검색. 미입력 또는 공백이면 전체 조회합니다.
    @Size(max = 254, message = "검색어는 254자 이내로 입력해야 합니다.")
    private String keyword;

    private MemberStatus status;
    private Role role;

    // 페이지 번호는 0부터 시작합니다.
    @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
    private int page = 0;

    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    private int size = 20;

    public String getKeyword() {
        return keyword == null ? null : keyword.strip();
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }
}
