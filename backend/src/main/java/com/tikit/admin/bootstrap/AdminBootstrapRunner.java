package com.tikit.admin.bootstrap;

import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Profile("(local | dev) & !prod")
@ConditionalOnProperty(prefix = "bootstrap.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AdminBootstrapProperties.class)
@RequiredArgsConstructor
public class AdminBootstrapRunner implements CommandLineRunner {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    @Override
    public void run(String... args) {
        String email = properties.getEmail().trim().toLowerCase(Locale.ROOT);
        var existing = memberRepository.findByEmail(email);
        if (existing.isPresent()) {
            synchronizeAdmin(existing.get());
            return;
        }
        Member admin = Member.create(email, passwordEncoder.encode(properties.getPassword()),
                properties.getName().strip(), Role.ADMIN);
        try {
            // Repository의 자체 트랜잭션으로 저장합니다.
            memberRepository.saveAndFlush(admin);
        } catch (DataIntegrityViolationException exception) {
            // 공용 DB에 여러 앱이 동시에 시작한 경우에도 같은 이메일의 ADMIN은 한 번만 생성합니다.
            if (!isDuplicateEmail(exception)) throw exception;
            Member concurrentlyCreated = memberRepository.findByEmail(email).orElseThrow(() -> exception);
            synchronizeAdmin(concurrentlyCreated);
        }
    }

    private void synchronizeAdmin(Member member) {
        if (member.getRole() != Role.ADMIN || member.getStatus() != MemberStatus.ACTIVE) {
            throw new IllegalStateException("초기 관리자 이메일이 기존 계정과 충돌합니다. 다른 이메일을 지정하세요.");
        }
        // 개발용 공용 계정만 지정된 비밀번호로 맞춥니다. 이미 일치하면 해시와 version을 유지합니다.
        if (!passwordEncoder.matches(properties.getPassword(), member.getPasswordHash())) {
            member.changePasswordHash(passwordEncoder.encode(properties.getPassword()));
            memberRepository.saveAndFlush(member);
        }
    }

    private boolean isDuplicateEmail(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "uq_members_email".equals(violation.getConstraintName())) return true;
        }
        return false;
    }
}
