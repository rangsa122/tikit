package com.tikit.member.controller;

import com.tikit.auth.entity.*;
import com.tikit.config.security.*;
import com.tikit.member.dto.MemberProfileResponseDTO;
import com.tikit.member.service.MemberProfileService;
import com.tikit.util.JWTProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MemberProfileController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class MemberProfileControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MemberProfileService profiles;
    @MockitoBean JWTProvider jwtProvider;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean com.tikit.auth.cookie.RefreshTokenCookie refreshTokenCookie;
    @MockitoBean org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

    @ParameterizedTest
    @EnumSource(Role.class)
    void authenticatedRolesCanReadOwnCurrentRoleWithoutRedirect(Role role) throws Exception {
        Member member = Member.create("member@example.com", "hash", "회원", role);
        when(profiles.getMyProfile(1L)).thenReturn(MemberProfileResponseDTO.from(member));
        var principal = new UsernamePasswordAuthenticationToken("1", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        mvc.perform(get("/api/v1/me").with(authentication(principal)))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.data.role").value(role.name()))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        verify(profiles).getMyProfile(1L);
    }

    @Test
    void returnsCurrentProfileRoleEvenWhenTokenRoleIsOlder() throws Exception {
        Member member = Member.create("member@example.com", "hash", "회원", Role.MANAGER);
        when(profiles.getMyProfile(1L)).thenReturn(MemberProfileResponseDTO.from(member));
        var principal = new UsernamePasswordAuthenticationToken("1", null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        mvc.perform(get("/api/v1/me").with(authentication(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("MANAGER"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }
}
