package com.toswap.toswap.config;

import com.toswap.toswap.security.CustomOAuth2UserService;
import com.toswap.toswap.security.OAuth2FailureHandler;
import com.toswap.toswap.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 전체 설정.
 *
 * 주요 설정:
 * - CSRF 비활성화: 세션 + SPA 구조에서는 CORS + SameSite 쿠키로 대체
 * - CORS: 프론트엔드 도메인에서 쿠키 포함 요청(withCredentials) 허용
 * - URL 접근 제어: /api/** 는 인증 필수, OAuth2 관련 경로는 허용
 * - OAuth2 로그인: 카카오 로그인 흐름 연결
 * - 예외 처리: 미인증/미인가 요청에 JSON 응답 반환 (HTML 리다이렉트 대신)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // SPA(React 등) + 세션 구조에서는 CSRF 토큰 대신 CORS 정책으로 보호
            .csrf(AbstractHttpConfigurer::disable)

            // CORS 설정 적용 (아래 corsConfigurationSource 빈 사용)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // URL별 접근 권한 설정
            .authorizeHttpRequests(auth -> auth
                // OAuth2 로그인 시작 경로와 콜백 경로는 인증 없이 접근 가능
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                // /api/** 하위는 모두 로그인 필요
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )

            // OAuth2 소셜 로그인 설정
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    // 카카오 응답을 받아 유저 저장/업데이트하는 서비스 연결
                    .userService(customOAuth2UserService)
                )
                // 로그인 성공 시 프론트엔드로 리다이렉트
                .successHandler(oAuth2SuccessHandler)
                // 로그인 실패 시 에러 페이지로 리다이렉트
                .failureHandler(oAuth2FailureHandler)
            )

            // 인증/인가 실패 시 HTML 리다이렉트 대신 JSON 응답 반환
            .exceptionHandling(exception -> exception
                // 미인증 요청 (로그인 안 된 상태로 /api/** 접근) → 401
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                        "{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}"
                    );
                })
                // 인증은 됐으나 권한 없음 → 403
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                        "{\"status\":403,\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}"
                    );
                })
            );

        return http.build();
    }

    /**
     * CORS 설정.
     * 프론트엔드에서 axios/fetch의 withCredentials: true 옵션으로 쿠키를 전송하려면
     * allowedOrigins에 정확한 도메인을, allowCredentials를 true로 설정해야 한다.
     * (allowedOrigins에 "*" 와 allowCredentials true는 함께 사용 불가)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 프론트엔드 도메인만 허용 (application.yaml의 app.frontend-url)
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 쿠키(세션) 포함 요청 허용
        config.setAllowCredentials(true);
        // preflight 캐시 시간 (초)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
