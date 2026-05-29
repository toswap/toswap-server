package com.toswap.toswap.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI / OpenAPI 3.0 설정.
 *
 * 접속: http://localhost:8080/swagger-ui.html
 *
 * 인증 방식: 카카오 OAuth2 로그인 후 브라우저에 SESSION 쿠키가 자동 저장됨.
 * Swagger UI는 같은 브라우저에서 열면 쿠키가 자동으로 포함되어 /api/** 호출 가능.
 * 별도 토큰 입력 없이 로그인 → Swagger UI 접속 순서로 테스트.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        // SESSION 쿠키 기반 인증 스킴 정의
        // Swagger UI에서 "Authorize" 버튼으로 SESSION 쿠키 값을 직접 입력할 수 있음
        SecurityScheme sessionCookieScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name("SESSION")
                .description("카카오 로그인 후 자동 발급되는 세션 쿠키. 같은 브라우저에서 로그인 후 테스트하면 자동 포함됨.");

        return new OpenAPI()
                .info(new Info()
                        .title("Toswap API")
                        .description("""
                                ## TOEIC Speaking 연습 서비스 API

                                ### 인증 방법
                                1. 새 탭에서 [카카오 로그인](http://localhost:8080/oauth2/authorization/kakao) 접속
                                2. 로그인 완료 후 이 페이지로 돌아오면 SESSION 쿠키가 자동 포함됨
                                3. 로그인 없이 `/api/**` 호출 시 401 응답

                                ### 파트별 문제 구성
                                | Part | 유형 | 준비 | 답변 |
                                |------|------|------|------|
                                | 1 | 소리내어 읽기 | 45s | 45s |
                                | 2 | 사진 묘사 (이미지 포함) | 45s | 30s |
                                | 3 | 질문 응답 | 3s | 30s |
                                | 4 | 정보 활용 응답 | 45s | 30s |
                                | 5 | 의견 표현 | 45s | 60s |
                                """)
                        .version("v1.0"))
                .components(new Components()
                        .addSecuritySchemes("sessionAuth", sessionCookieScheme))
                // 모든 엔드포인트에 세션 인증 적용
                .addSecurityItem(new SecurityRequirement().addList("sessionAuth"));
    }
}
