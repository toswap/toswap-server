package com.toswap.toswap.controller;

import com.toswap.toswap.dto.request.ProfileUpdateRequest;
import com.toswap.toswap.dto.response.UserResponse;
import com.toswap.toswap.security.CustomOAuth2User;
import com.toswap.toswap.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * GET /api/auth/me
     * 현재 세션에 로그인된 유저 정보 반환.
     *
     * @AuthenticationPrincipal: SecurityContext에 저장된 CustomOAuth2User를 자동으로 주입받음.
     * SecurityConfig에서 /api/** 는 인증 필수로 설정되어 있으므로 여기서는 null 체크 불필요.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        return ResponseEntity.ok(authService.getMe(oAuth2User.getUserId()));
    }

    /**
     * POST /api/auth/logout
     * 서버 세션 무효화 + SecurityContext 초기화.
     * Redis에 저장된 세션 데이터도 함께 삭제된다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // Spring Security의 로그아웃 처리 (세션 무효화 + SecurityContext 초기화)
        new SecurityContextLogoutHandler().logout(
                request, response, SecurityContextHolder.getContext().getAuthentication()
        );
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH /api/auth/profile
     * 카카오 로그인 후 이메일이 없는 유저의 추가 정보 입력.
     * 프론트엔드에서 hasEmail=false 인 유저를 /additional-info 페이지로 유도한 뒤 호출.
     *
     * @Valid: ProfileUpdateRequest의 @NotBlank, @Email 유효성 검사 실행.
     */
    @PatchMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            @RequestBody @Valid ProfileUpdateRequest request) {
        return ResponseEntity.ok(authService.updateProfile(oAuth2User.getUserId(), request));
    }
}
