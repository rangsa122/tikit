package com.tikit.admin.repository;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AdminMemberSpecifications {

    private AdminMemberSpecifications() {
    }

    public static Specification<Member> search(String keyword, MemberStatus status, Role role) {
        return (root, query, builder) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                // LIKE의 특수문자도 사용자가 입력한 문자 그대로 검색합니다.
                String escaped = keyword.strip().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_");
                String pattern = "%" + escaped + "%";
                conditions.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern, '!'),
                        builder.like(builder.lower(root.get("email")), pattern, '!')
                ));
            }
            if (status != null) {
                conditions.add(builder.equal(root.get("status"), status));
            }
            if (role != null) {
                conditions.add(builder.equal(root.get("role"), role));
            }
            return builder.and(conditions.toArray(Predicate[]::new));
        };
    }
}
