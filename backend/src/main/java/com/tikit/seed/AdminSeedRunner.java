package com.tikit.seed;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 로그인할 수 있는 테스트 ADMIN 계정을 만든다.
 * SEED_ADMIN_ENABLED=true와 이메일·비밀번호 환경변수를 넣고 켰을 때만 실행한다.
 * 같은 이메일이 이미 있으면 아무것도 하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tikit.seed.admin.enabled", havingValue = "true")
public class AdminSeedRunner implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${tikit.seed.admin.email:}")
    private String email;

    @Value("${tikit.seed.admin.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            log.info("테스트 ADMIN 계정 설정이 없어 건너뜁니다.");
            return;
        }

        // members 테이블에 email = lower(btrim(email)) 제약이 있다
        String normalized = email.trim().toLowerCase(Locale.ROOT);

        if (memberRepository.existsByEmail(normalized)) {
            log.info("테스트 ADMIN 계정이 이미 있습니다. email={}", normalized);
            return;
        }

        memberRepository.save(Member.create(
                normalized,
                passwordEncoder.encode(password),
                "테스트 관리자",
                Role.ADMIN));

        log.info("테스트 ADMIN 계정을 만들었습니다. email={}", normalized);
    }
}