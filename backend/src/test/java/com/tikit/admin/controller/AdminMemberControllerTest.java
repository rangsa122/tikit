package com.tikit.admin.controller;

import com.tikit.admin.controller.advice.AdminMemberControllerAdvice;
import com.tikit.admin.entity.AuditLog;
import com.tikit.admin.repository.AuditLogRepository;
import com.tikit.admin.service.AdminMemberService;
import com.tikit.auth.dto.request.MemberLoginRequestDTO;
import com.tikit.auth.entity.Member;
import com.tikit.auth.entity.MemberStatus;
import com.tikit.auth.entity.Role;
import com.tikit.auth.exception.AccountRestrictedException;
import com.tikit.auth.redis.RefreshTokenRepository;
import com.tikit.auth.repository.MemberRepository;
import com.tikit.auth.service.MemberService;
import com.tikit.common.advice.GlobalExceptionHandler;
import com.tikit.config.security.CustomAccessDeniedHandler;
import com.tikit.config.security.CustomAuthenticationEntryPoint;
import com.tikit.config.security.CustomUserDetails;
import com.tikit.config.security.SecurityConfig;
import com.tikit.filter.JWTCheckFilter;
import com.tikit.util.JWTProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AdminMemberControllerTest.TestConfig.class)
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Transactional
class AdminMemberControllerTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Member.class, AuditLog.class})
    @EnableJpaRepositories(basePackageClasses = {MemberRepository.class, AuditLogRepository.class})
    @EnableJpaAuditing
    @Import({AdminMemberController.class, AdminMemberService.class, MemberService.class,
            AdminMemberControllerAdvice.class, GlobalExceptionHandler.class,
            SecurityConfig.class, JWTCheckFilter.class, JWTProvider.class,
            CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
    static class TestConfig {
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private MemberService memberService;

    @Autowired
    private JWTProvider jwtProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    private String prefix;
    private long auditLogCountBefore;

    private Member alice;
    private Member bob;
    private Member manager;
    private Member withdrawn;
    private String adminToken;

    @BeforeEach
    void setUp() {
        // 기존 회원과 겹치지 않게 테스트 회원마다 접두사 붙임
        prefix = "t" + UUID.randomUUID().toString().substring(0, 8);
        auditLogCountBefore = auditLogRepository.count();

        alice = saveMember("alice", prefix + " 앨리스", Role.MEMBER, MemberStatus.ACTIVE);
        bob = saveMember("bob", prefix + " 밥", Role.MEMBER, MemberStatus.SUSPENDED);
        manager = saveMember("manager", prefix + " 운영자", Role.MANAGER, MemberStatus.ACTIVE);
        withdrawn = saveMember("withdrawn", prefix + " 탈퇴회원", Role.MEMBER, MemberStatus.WITHDRAWN);
        adminToken = token(Role.ADMIN);
    }

    @Test
    @DisplayName("회원 목록은 기본 페이지로 조회하고 민감정보를 제외한다")
    void listMembers() throws Exception {
        performAsAdmin(get("/api/v1/admin/members").param("keyword", prefix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("customerKey"))))
                .andExpect(content().string(not(containsString("secret-password-hash"))));
    }

    @Test
    @DisplayName("가입 시각이 같으면 ID 내림차순으로 정렬하고 페이지를 나눈다")
    void listPaginatesWithStableOrder() throws Exception {
        jdbcTemplate.update("UPDATE members SET created_at = TIMESTAMP '2026-01-01 00:00:00' WHERE email LIKE ?",
                prefix + "%");

        performAsAdmin(get("/api/v1/admin/members")
                .param("keyword", prefix).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items[0].memberId").value(bob.getId()))
                .andExpect(jsonPath("$.data.items[1].memberId").value(alice.getId()));
    }

    @Test
    @DisplayName("이메일 검색은 대소문자를 구분하지 않고 검색어 앞뒤 공백을 제거한다")
    void searchByEmail() throws Exception {
        String keyword = "  " + (prefix + "-alice@example").toUpperCase() + "  ";

        performAsAdmin(get("/api/v1/admin/members").param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].memberId").value(alice.getId()));
    }

    @Test
    @DisplayName("이름 검색과 상태, 역할 필터를 함께 적용한다")
    void searchByNameWithFilters() throws Exception {
        performAsAdmin(get("/api/v1/admin/members")
                .param("keyword", prefix + " 운영").param("role", "MANAGER").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].memberId").value(manager.getId()));

        performAsAdmin(get("/api/v1/admin/members")
                .param("keyword", prefix + " 운영").param("status", "SUSPENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("검색어의 LIKE 특수문자는 와일드카드가 아니라 입력한 문자 그대로 검색한다")
    void searchTreatsPercentAsLiteral() throws Exception {
        Member literal = saveMember("literal", prefix + " 문자%", Role.MEMBER, MemberStatus.ACTIVE);
        saveMember("decoy", prefix + " 문자X", Role.MEMBER, MemberStatus.ACTIVE);

        performAsAdmin(get("/api/v1/admin/members").param("keyword", prefix + " 문자%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].memberId").value(literal.getId()));
    }

    @Test
    @DisplayName("회원 상세는 상태와 일시 정보를 포함하고 민감정보를 제외한다")
    void memberDetail() throws Exception {
        performAsAdmin(get("/api/v1/admin/members/{id}", withdrawn.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(withdrawn.getId()))
                .andExpect(jsonPath("$.data.accountStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.version").isNumber())
                .andExpect(jsonPath("$.data.withdrawnAt").isNotEmpty())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.customerKey").doesNotExist());
    }

    @Test
    @DisplayName("존재하지 않는 회원은 404 RESOURCE_NOT_FOUND를 반환한다")
    void missingMemberReturns404() throws Exception {
        performAsAdmin(get("/api/v1/admin/members/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest
    @DisplayName("잘못된 검색 조건은 400 VALIDATION_ERROR를 반환한다")
    @CsvSource({"page,-1", "size,101", "role,SELLER"})
    void invalidSearchParametersReturn400(String parameter, String value) throws Exception {
        performAsAdmin(get("/api/v1/admin/members").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("비로그인 사용자의 조회와 상태 변경은 401을 반환한다")
    void anonymousRequestsReturn401() throws Exception {
        mvc.perform(get("/api/v1/admin/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mvc.perform(patch("/api/v1/admin/members/{id}/status", alice.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("SUSPENDED", 0L, "사유")))
                .andExpect(status().isUnauthorized());

        assertThat(newAuditLogCount()).isZero();
    }

    @ParameterizedTest
    @DisplayName("MEMBER와 MANAGER의 조회와 상태 변경은 403을 반환한다")
    @EnumSource(value = Role.class, names = {"MEMBER", "MANAGER"})
    void nonAdminRequestsReturn403(Role role) throws Exception {
        String accessToken = token(role);

        mvc.perform(get("/api/v1/admin/members").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(patch("/api/v1/admin/members/{id}/status", alice.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("SUSPENDED", 0L, "사유")))
                .andExpect(status().isForbidden());

        assertThat(newAuditLogCount()).isZero();
    }

    @Test
    @DisplayName("활성 회원을 정지하고 처리자, 사유, 전후 상태를 감사 이력에 저장한다")
    void suspendMember() throws Exception {
        Member admin = usePersistedAdmin();

        changeStatus(alice.getId(), "SUSPENDED", "  운영 정책 위반  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("SUSPENDED"));
        entityManager.clear();

        assertThat(memberRepository.findById(alice.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.SUSPENDED);

        AuditLog log = auditLogOf(alice.getId());
        assertThat(log.getActorMemberId()).isEqualTo(admin.getId());
        assertThat(log.getAction()).isEqualTo("MEMBER_SUSPEND");
        assertThat(log.getReason()).isEqualTo("운영 정책 위반");
        assertThat(log.getBeforeState()).containsEntry("status", "ACTIVE");
        assertThat(log.getAfterState()).containsEntry("status", "SUSPENDED");
        verify(refreshTokenRepository).delete(alice.getId().toString());
    }

    @Test
    @DisplayName("조회 이후 버전이 바뀌었으면 409 VERSION_CONFLICT를 반환하고 상태를 바꾸지 않는다")
    void staleVersionReturns409() throws Exception {
        usePersistedAdmin();
        Long staleVersion = alice.getVersion() + 1;

        changeStatus(alice.getId(), "SUSPENDED", staleVersion, "버전 불일치")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        assertThat(memberRepository.findById(alice.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
        assertThat(newAuditLogCount()).isZero();
    }

    @Test
    @DisplayName("정지 회원을 활성 상태로 복구하고 해제 이력을 저장한다")
    void resumeMember() throws Exception {
        usePersistedAdmin();

        changeStatus(bob.getId(), "ACTIVE", "정지 해제 승인")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));
        entityManager.clear();

        AuditLog log = auditLogOf(bob.getId());
        assertThat(log.getAction()).isEqualTo("MEMBER_RESUME");
        assertThat(log.getBeforeState()).containsEntry("status", "SUSPENDED");
        assertThat(log.getAfterState()).containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("중복 정지, 중복 해제, 탈퇴 회원 변경, 탈퇴 처리 요청은 409로 거부하고 이력을 남기지 않는다")
    void invalidTransitionsReturn409() throws Exception {
        usePersistedAdmin();

        changeStatus(bob.getId(), "SUSPENDED", "중복 정지").andExpect(status().isConflict());
        changeStatus(alice.getId(), "ACTIVE", "중복 해제").andExpect(status().isConflict());
        changeStatus(withdrawn.getId(), "ACTIVE", "탈퇴 회원 복구 시도").andExpect(status().isConflict());
        changeStatus(alice.getId(), "WITHDRAWN", "탈퇴 처리 시도")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        assertThat(memberRepository.findById(withdrawn.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(newAuditLogCount()).isZero();
    }

    @Test
    @DisplayName("자기 계정과 다른 관리자 계정의 상태 변경은 403으로 차단한다")
    void adminAccountsAreProtected() throws Exception {
        Member admin = usePersistedAdmin();
        Member otherAdmin = saveMember("other-admin", prefix + " 다른 관리자", Role.ADMIN, MemberStatus.ACTIVE);

        for (Long id : new Long[]{admin.getId(), otherAdmin.getId()}) {
            changeStatus(id, "SUSPENDED", "관리자 정지 시도")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ADMIN_ACCOUNT_PROTECTED"));
        }
        assertThat(newAuditLogCount()).isZero();
    }

    @Test
    @DisplayName("ADMIN 토큰이 있어도 정지된 관리자는 상태를 변경할 수 없다")
    void suspendedAdminCannotChangeStatus() throws Exception {
        Member admin = usePersistedAdmin();
        ReflectionTestUtils.setField(admin, "status", MemberStatus.SUSPENDED);
        memberRepository.flush();

        changeStatus(alice.getId(), "SUSPENDED", "권한 차단 확인")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED"));
        assertThat(newAuditLogCount()).isZero();
    }

    @ParameterizedTest
    @DisplayName("상태, 버전, 사유가 없는 요청은 400을 반환한다")
    @ValueSource(strings = {"{}", "{\"status\":\"SUSPENDED\",\"reason\":\"사유\"}",
            "{\"status\":\"SUSPENDED\",\"expectedVersion\":0,\"reason\":\"   \"}"})
    void invalidStatusChangeBodyReturns400(String body) throws Exception {
        usePersistedAdmin();

        performAsAdmin(patch("/api/v1/admin/members/{id}/status", alice.getId())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(newAuditLogCount()).isZero();
    }

    @Test
    @DisplayName("정지하면 로그인이 차단되고 해제하면 다시 로그인할 수 있다")
    void suspensionIsReflectedInLogin() throws Exception {
        usePersistedAdmin();
        String password = "Example-password-2026!";
        ReflectionTestUtils.setField(alice, "passwordHash", passwordEncoder.encode(password));
        memberRepository.flush();
        when(userDetailsService.loadUserByUsername(alice.getEmail())).thenAnswer(invocation ->
                new CustomUserDetails(memberRepository.findByEmail(alice.getEmail()).orElseThrow()));
        MemberLoginRequestDTO request = new MemberLoginRequestDTO(alice.getEmail(), password);

        changeStatus(alice.getId(), "SUSPENDED", "정지 승인").andExpect(status().isOk());
        entityManager.clear();
        assertThatThrownBy(() -> memberService.login(request)).isInstanceOf(AccountRestrictedException.class);

        changeStatus(alice.getId(), "ACTIVE", "해제 승인").andExpect(status().isOk());
        entityManager.clear();
        assertThat(memberService.login(request).getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("정지 전에 발급한 리프레시 토큰도 재발급 시 차단하고 저장된 세션을 삭제한다")
    void suspensionBlocksExistingRefreshToken() throws Exception {
        usePersistedAdmin();
        String refreshToken = jwtProvider.generateRefreshToken(alice);

        changeStatus(alice.getId(), "SUSPENDED", "정지 승인").andExpect(status().isOk());
        entityManager.clear();

        assertThatThrownBy(() -> memberService.refresh(refreshToken))
                .isInstanceOf(AccountRestrictedException.class);
        verify(refreshTokenRepository, atLeastOnce()).delete(alice.getId().toString());
        verify(refreshTokenRepository, never()).rotate(anyString(), anyString(), anyString());
    }

    private Member saveMember(String emailName, String name, Role role, MemberStatus status) {
        Member member = Member.create(prefix + "-" + emailName + "@example.com", "secret-password-hash", name, role);
        ReflectionTestUtils.setField(member, "status", status);
        if (status == MemberStatus.WITHDRAWN) {
            ReflectionTestUtils.setField(member, "withdrawnAt", Instant.now());
        }
        return memberRepository.saveAndFlush(member);
    }

    private Member usePersistedAdmin() {
        Member admin = saveMember("admin", prefix + " 관리자", Role.ADMIN, MemberStatus.ACTIVE);
        adminToken = jwtProvider.generateAccessToken(admin);
        return admin;
    }

    // 권한 확인용 토큰
    private String token(Role role) {
        Member member = Member.create(prefix + "-token@example.com", "unused", "인증 사용자", role);
        ReflectionTestUtils.setField(member, "id", 999999L);
        return jwtProvider.generateAccessToken(member);
    }

    private ResultActions changeStatus(Long memberId, String newStatus, String reason) throws Exception {
        Long version = memberRepository.findById(memberId).map(Member::getVersion).orElse(0L);
        return changeStatus(memberId, newStatus, version, reason);
    }

    private ResultActions changeStatus(Long memberId, String newStatus, Long version, String reason) throws Exception {
        return performAsAdmin(patch("/api/v1/admin/members/{id}/status", memberId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusBody(newStatus, version, reason)));
    }

    private ResultActions performAsAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + adminToken));
    }

    private String statusBody(String status, Long version, String reason) {
        return "{\"status\":\"" + status + "\",\"expectedVersion\":" + version
                + ",\"reason\":\"" + reason + "\"}";
    }

    // 테스트 중에 추가된 감사 이력 수
    private long newAuditLogCount() {
        return auditLogRepository.count() - auditLogCountBefore;
    }

    private AuditLog auditLogOf(Long targetId) {
        return auditLogRepository.findAll().stream()
                .filter(log -> log.getTargetId().equals(targetId))
                .findFirst()
                .orElseThrow();
    }
}
