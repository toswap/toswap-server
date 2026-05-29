package com.toswap.toswap.controller;

import com.toswap.toswap.dto.request.ProfileUpdateRequest;
import com.toswap.toswap.dto.response.UserResponse;
import com.toswap.toswap.security.CustomOAuth2User;
import com.toswap.toswap.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 API - 로그인은 /oauth2/authorization/kakao 로 진행")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "내 정보 조회",
            description = "현재 로그인한 유저의 정보를 반환한다. hasEmail=false 이면 /additional-info 로 유도해야 함."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "로그인 필요")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        return ResponseEntity.ok(authService.getMe(oAuth2User.getUserId()));
    }

    @Operation(
            summary = "로그아웃",
            description = "서버 세션을 무효화하고 Redis에서 세션 데이터를 삭제한다."
    )
    @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(
                request, response, SecurityContextHolder.getContext().getAuthentication()
        );
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "이메일 등록 (추가 정보 입력)",
            description = """
                    카카오 비즈앱 미등록으로 이메일을 받지 못한 유저가 이메일을 직접 입력하는 엔드포인트.
                    이미 이메일이 등록된 유저는 호출 불가 (400 반환).
                    프론트엔드에서 hasEmail=false 일 때 /additional-info 페이지로 유도 후 호출.
                    """
    )
    @ApiResponse(responseCode = "200", description = "이메일 등록 성공")
    @ApiResponse(responseCode = "400", description = "이미 이메일이 있거나 이메일 중복")
    @PatchMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal CustomOAuth2User oAuth2User,
            @RequestBody @Valid ProfileUpdateRequest request) {
        return ResponseEntity.ok(authService.updateProfile(oAuth2User.getUserId(), request));
    }
}
