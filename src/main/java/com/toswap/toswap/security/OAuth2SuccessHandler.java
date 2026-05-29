package com.toswap.toswap.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 로그인 성공 시 실행되는 핸들러.
 * 로그인 후 어디로 리다이렉트할지 결정한다.
 *
 * 세션은 Spring Security가 이 핸들러 호출 전에 자동으로 생성하므로
 * 여기서는 리다이렉트 URL 결정만 담당한다.
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {

        // SecurityContext에서 로그인한 유저 정보 꺼내기
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        // 카카오 비즈앱 미등록 → 이메일 없음 → 추가 정보 입력 페이지로 이동
        // 이메일이 있으면 메인 페이지로 바로 이동
        String targetUrl = oAuth2User.getUser().hasEmail()
                ? frontendUrl
                : frontendUrl + "/additional-info";

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
