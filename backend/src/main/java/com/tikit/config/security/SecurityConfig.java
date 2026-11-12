
package com.tikit.config.security;

import com.tikit.filter.JWTCheckFilter;
import com.tikit.util.JWTProvider;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JWTProvider jwtProvider,
            ObjectMapper objectMapper,
            JWTCheckFilter jwtCheckFilter,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler
    ) throws Exception {

        http
                .cors(cors -> cors.disable())
                .csrf(csrf -> csrf.disable())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth ->
                        auth
                                .dispatcherTypeMatchers(
                                        DispatcherType.ERROR
                                ).permitAll()

                                // 로그인, 회원가입 및 Swagger
                                .requestMatchers(
                                        "/api/v1/auth/login",
                                        "/api/v1/auth/signup",
                                        "/api/v1/auth/refresh",
                                        "/swagger-ui.html",
                                        "/swagger-ui/**",
                                        "/v3/api-docs",
                                        "/v3/api-docs/**"
                                ).permitAll()

                                // 기존 회차별 좌석 조회 API
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/v1/auth/email-availability",
                                        "/api/v1/sessions/*/seats"
                                ).permitAll()

                                // 장르 목록/카테고리 비로그인 사용자도 조회
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/v1/genres"
                                ).permitAll()

                                // 일반 사용자용 공개 공연 상세 조회
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/v1/performances/*"
                                ).permitAll()

                                // 일반 사용자용 공개 공연 회차 조회
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/v1/performances/*/sessions"
                                ).permitAll()

                                // 관리자 전용 API
                                .requestMatchers(
                                        "/api/v1/admin/**"
                                ).hasRole("ADMIN")

                                // 대기열은 일반 회원만
                                .requestMatchers(
                                        "/api/v1/sessions/*/queue-entries",
                                        "/api/v1/queue-entries/**"
                                ).hasRole("MEMBER")

                                // 좌석 임시 선점은 일반 회원만
                                .requestMatchers(
                                        "/api/v1/sessions/*/seats/*/hold"
                                ).hasRole("MEMBER")

                                // 예매 조회는 일반 회원만
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/v1/me/bookings",
                                        "/api/v1/bookings/*",
                                        "/api/v1/bookings/*/tickets"
                                ).hasRole("MEMBER")

                                // 나머지 API는 인증 필요
                                .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtCheckFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    // JWT 필터 중복 등록 방지
    @Bean
    public FilterRegistrationBean<JWTCheckFilter> jwtCheckFilterRegistration(
            JWTCheckFilter jwtCheckFilter
    ) {
        FilterRegistrationBean<JWTCheckFilter> registration =
                new FilterRegistrationBean<>(jwtCheckFilter);

        registration.setEnabled(false);

        return registration;
    }
}
