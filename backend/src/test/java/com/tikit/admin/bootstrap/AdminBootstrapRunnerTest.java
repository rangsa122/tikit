package com.tikit.admin.bootstrap;

import com.tikit.auth.entity.*;
import com.tikit.auth.repository.MemberRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {
    @Mock MemberRepository members;
    @Mock PasswordEncoder encoder;
    AdminBootstrapRunner runner;
    AdminBootstrapProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AdminBootstrapProperties();
        properties.setEmail("Test1234@naver.com");
        properties.setPassword("bootstrap-test-password");
        runner = new AdminBootstrapRunner(members, encoder, properties);
    }

    @Test
    void insertsActiveAdminUsingEncodedPassword() {
        when(encoder.encode("bootstrap-test-password")).thenReturn("encoded");
        runner.run();
        ArgumentCaptor<Member> inserted = ArgumentCaptor.forClass(Member.class);
        verify(members).saveAndFlush(inserted.capture());
        assertThat(inserted.getValue().getEmail()).isEqualTo("test1234@naver.com");
        assertThat(inserted.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(inserted.getValue().getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(inserted.getValue().getPasswordHash()).isEqualTo("encoded");
        assertThat(inserted.getValue().getCustomerKey()).isNotBlank();
    }

    @Test
    void restartingDoesNotRewriteMatchingSharedPassword() {
        Member admin = Member.create("test1234@naver.com", "original-hash", "관리자", Role.ADMIN);
        when(members.findByEmail("test1234@naver.com")).thenReturn(Optional.of(admin));
        when(encoder.matches("bootstrap-test-password", "original-hash")).thenReturn(true);
        runner.run();
        runner.run();
        verify(members, never()).saveAndFlush(any());
        verify(encoder, never()).encode(anyString());
        assertThat(admin.getPasswordHash()).isEqualTo("original-hash");
    }

    @ParameterizedTest
    @CsvSource({"MEMBER,ACTIVE", "MANAGER,ACTIVE", "ADMIN,SUSPENDED", "ADMIN,WITHDRAWN"})
    void refusesExistingNonAdminOrRestrictedAccount(Role role, MemberStatus status) {
        Member existing = Member.create("test1234@naver.com", "original", "회원", role);
        ReflectionTestUtils.setField(existing, "status", status);
        when(members.findByEmail("test1234@naver.com")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> runner.run()).isInstanceOf(IllegalStateException.class);
        assertThat(existing.getRole()).isEqualTo(role);
        assertThat(existing.getStatus()).isEqualTo(status);
        verify(members, never()).saveAndFlush(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void toleratesConcurrentCreationOfSameActiveAdmin() {
        when(encoder.encode(anyString())).thenReturn("encoded");
        when(encoder.matches("bootstrap-test-password", "other-hash")).thenReturn(true);
        when(members.findByEmail("test1234@naver.com")).thenReturn(Optional.empty(), Optional.of(
                Member.create("test1234@naver.com", "other-hash", "관리자", Role.ADMIN)));
        var violation = new ConstraintViolationException("duplicate", new SQLException("duplicate", "23505"), "uq_members_email");
        when(members.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", violation));
        assertThatCode(() -> runner.run()).doesNotThrowAnyException();
    }

    @Test
    void doesNotSwallowUnrelatedDatabaseFailure() {
        when(encoder.encode(anyString())).thenReturn("encoded");
        when(members.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("other constraint"));
        assertThatThrownBy(() -> runner.run()).isInstanceOf(DataIntegrityViolationException.class);
    }

    private ApplicationContextRunner context(String... profiles) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles))
                .withUserConfiguration(AdminBootstrapRunner.class)
                .withBean(MemberRepository.class, () -> members)
                .withBean(PasswordEncoder.class, () -> encoder);
    }

    @Test
    void canBeDisabledExplicitly() {
        context("local").withPropertyValues("bootstrap.admin.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AdminBootstrapRunner.class));
        verifyNoInteractions(members, encoder);
    }

    @Test
    void neverEnabledInProductionEvenWithLocalProfile() {
        context("local", "prod").withPropertyValues("bootstrap.admin.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(AdminBootstrapRunner.class));
    }

    @Test
    void enabledWithSharedCredentialsWithoutExtraSettings() {
        context("local")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AdminBootstrapRunner.class);
                    assertThat(context.getBean(AdminBootstrapProperties.class).getEmail()).isEqualTo("test1234@naver.com");
                    assertThat(context.getBean(AdminBootstrapProperties.class).getPassword()).isEqualTo("123456789a");
                });
    }

    @Test
    void refusesEmptyPasswordWithoutCreatingAccount() {
        context("local").withPropertyValues("bootstrap.admin.password=")
                .run(context -> assertThat(context).hasFailed());
        verifyNoInteractions(members, encoder);
    }

    @Test
    void rejectsLongMultibytePasswordWithoutIncludingPasswordInError() {
        String password = "가".repeat(25);
        context("local").withPropertyValues("bootstrap.admin.enabled=true", "bootstrap.admin.password=" + password)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("passwordValid")
                            .hasStackTraceContaining("72");
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("false");
                    assertThat(context.getStartupFailure().toString()).doesNotContain(password);
                });
    }

    @Test
    void updatesExistingAdminToSharedPassword() {
        Member admin = Member.create("test1234@naver.com", "old-hash", "관리자", Role.ADMIN);
        when(members.findByEmail("test1234@naver.com")).thenReturn(Optional.of(admin));
        when(encoder.encode("bootstrap-test-password")).thenReturn("shared-hash");
        runner.run();
        assertThat(admin.getPasswordHash()).isEqualTo("shared-hash");
        verify(members).saveAndFlush(admin);
    }

    @Test
    void defaultProfileDoesNotCreateSharedAdmin() {
        context().run(context -> assertThat(context).doesNotHaveBean(AdminBootstrapRunner.class));
        verifyNoInteractions(members, encoder);
    }
}
