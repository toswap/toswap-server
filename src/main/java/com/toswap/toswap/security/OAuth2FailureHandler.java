package com.toswap.toswap.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 로그인 실패 시 실행되는 핸들러.
 * 카카오 인증 거부, 네트워크 오류 등 실패 상황에서 프론트엔드 로그인 페이지로 리다이렉트한다.
 */
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        // 프론트엔드에서 error 쿼리 파라미터를 감지해 실패 메시지를 보여줄 수 있도록 전달
        getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/login?error=true");
    }
}
